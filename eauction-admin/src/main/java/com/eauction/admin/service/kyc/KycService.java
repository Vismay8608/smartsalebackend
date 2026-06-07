package com.eauction.admin.service.kyc;

import com.eauction.admin.dto.kyc.KycApproveRequest;
import com.eauction.admin.dto.kyc.KycRejectRequest;
import com.eauction.admin.dto.kyc.KycSubmitRequest;
import com.eauction.admin.entity.ClientRegister;
import com.eauction.admin.entity.ClientUserKyc;
import com.eauction.admin.repository.ClientRegisterRepository;
import com.eauction.admin.repository.ClientUserKycRepository;
import com.eauction.common.enums.KycStatus;
import com.eauction.common.exception.AppException;
import com.eauction.common.response.ResponseCode;
import com.eauction.common.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycService {

    private static final Set<String> COMPANY_SUBJECTS = Set.of("SELLER_COMPANY", "BUYER_COMPANY");

    private final ClientUserKycRepository kycRepository;
    private final ClientRegisterRepository clientRegisterRepository;

    @Transactional
    public Map<String, Object> submit(Integer clientId, Integer userId, KycSubmitRequest req, Integer actorId) {
        ClientRegister client = clientRegisterRepository.findById(clientId)
                .orElseThrow(() -> AppException.notFound(ResponseCode.CLIENT_NOT_FOUND));

        boolean isCompanySubject = COMPANY_SUBJECTS.contains(req.kycSubject());
        Integer subjectUserId = isCompanySubject ? null : userId;
        if (!isCompanySubject && subjectUserId == null) {
            throw AppException.badRequest(ResponseCode.INVALID_INPUT);
        }

        kycRepository.findTopByClientClientIdAndKycSubjectAndUserIdOrderBySubmittedAtDesc(
                        clientId, req.kycSubject(), subjectUserId)
                .filter(existing -> existing.getKycStatus() == KycStatus.PENDING)
                .ifPresent(existing -> {
                    throw AppException.conflict(ResponseCode.KYC_ALREADY_SUBMITTED);
                });

        ClientUserKyc kyc = ClientUserKyc.builder()
                .client(client)
                .userId(subjectUserId)
                .kycSubject(req.kycSubject())
                .kycStatus(KycStatus.PENDING)
                .fullName(req.fullName())
                .dateOfBirth(req.dateOfBirth())
                .remarks(req.remarks())
                .createdBy(actorId)
                .build();

        applyMaskedHash(req.panNumber(), HashUtil::maskPan, kyc::setPanMasked, kyc::setPanHash);
        applyMaskedHash(req.aadhaarNumber(), KycService::maskAadhaar, kyc::setAadhaarMasked, kyc::setAadhaarHash);
        applyMaskedHash(req.gstNumber(), KycService::maskGst, kyc::setGstMasked, kyc::setGstHash);
        applyMaskedHash(req.cinNumber(), KycService::maskCin, kyc::setCinMasked, kyc::setCinHash);

        kyc = kycRepository.save(kyc);
        log.info("KYC submitted [kycId={}] [clientId={}] [subject={}] [userId={}]",
                kyc.getKycId(), clientId, req.kycSubject(), subjectUserId);
        return toSummary(kyc);
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> list(Integer clientId, KycStatus status, Pageable pageable) {
        Page<ClientUserKyc> page = (status != null)
                ? kycRepository.findByClientClientIdAndKycStatus(clientId, status, pageable)
                : kycRepository.findByClientClientId(clientId, pageable);
        return page.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Integer clientId, Integer kycId) {
        return toSummary(loadOwnedByClient(clientId, kycId));
    }

    @Transactional
    public Map<String, Object> approve(Integer clientId, Integer kycId, KycApproveRequest req, Integer reviewerId) {
        ClientUserKyc kyc = loadPendingForReview(clientId, kycId);
        kyc.setKycStatus(KycStatus.VERIFIED);
        kyc.setExpiryDate(LocalDate.now().plusYears(1));
        kyc.setRejectionReason(null);
        if (req.remarks() != null && !req.remarks().isBlank()) {
            kyc.setRemarks(req.remarks());
        }
        kyc = kycRepository.save(kyc);
        log.info("KYC approved [kycId={}] [reviewer={}] [expiryDate={}]", kycId, reviewerId, kyc.getExpiryDate());
        return toSummary(kyc);
    }

    @Transactional
    public Map<String, Object> reject(Integer clientId, Integer kycId, KycRejectRequest req, Integer reviewerId) {
        ClientUserKyc kyc = loadPendingForReview(clientId, kycId);
        kyc.setRejectionReason(req.reason());
        kyc = kycRepository.save(kyc);
        log.info("KYC rejected [kycId={}] [reviewer={}] [reason={}]", kycId, reviewerId, req.reason());
        return toSummary(kyc);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ClientUserKyc loadOwnedByClient(Integer clientId, Integer kycId) {
        return kycRepository.findById(kycId)
                .filter(k -> k.getClient().getClientId().equals(clientId))
                .orElseThrow(() -> AppException.notFound(ResponseCode.KYC_NOT_FOUND));
    }

    private ClientUserKyc loadPendingForReview(Integer clientId, Integer kycId) {
        ClientUserKyc kyc = loadOwnedByClient(clientId, kycId);
        if (kyc.getKycStatus() != KycStatus.PENDING) {
            throw AppException.conflict(ResponseCode.KYC_ALREADY_REVIEWED);
        }
        return kyc;
    }

    private void applyMaskedHash(String rawValue,
                                 Function<String, String> mask,
                                 Consumer<String> setMasked,
                                 Consumer<String> setHash) {
        if (rawValue == null || rawValue.isBlank()) return;
        setMasked.accept(mask.apply(rawValue));
        setHash.accept(HashUtil.sha512_256Hex(rawValue));
    }

    // HashUtil.maskAadhaar yields "XXXX-XXXX-1234" (14 chars), which overflows
    // this table's aadhaar_masked VARCHAR(12) column — use a column-fitting format instead.
    private static String maskAadhaar(String aadhaar) {
        if (aadhaar == null || aadhaar.length() < 4) return "****";
        return "*".repeat(aadhaar.length() - 4) + aadhaar.substring(aadhaar.length() - 4);
    }

    private static String maskGst(String gst) {
        if (gst == null || gst.length() < 4) return "****";
        return "*".repeat(gst.length() - 4) + gst.substring(gst.length() - 4);
    }

    private static String maskCin(String cin) {
        if (cin == null || cin.length() < 4) return "****";
        return "*".repeat(cin.length() - 4) + cin.substring(cin.length() - 4);
    }

    private Map<String, Object> toSummary(ClientUserKyc k) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kycId", k.getKycId());
        m.put("clientId", k.getClient().getClientId());
        m.put("userId", k.getUserId());
        m.put("kycSubject", k.getKycSubject());
        m.put("kycStatus", k.getKycStatus());
        m.put("fullName", k.getFullName());
        m.put("dateOfBirth", k.getDateOfBirth());
        m.put("panMasked", k.getPanMasked());
        m.put("aadhaarMasked", k.getAadhaarMasked());
        m.put("gstMasked", k.getGstMasked());
        m.put("cinMasked", k.getCinMasked());
        m.put("expiryDate", k.getExpiryDate());
        m.put("rejectionReason", k.getRejectionReason());
        m.put("remarks", k.getRemarks());
        m.put("submittedAt", k.getSubmittedAt());
        m.put("createdBy", k.getCreatedBy());
        return m;
    }
}
