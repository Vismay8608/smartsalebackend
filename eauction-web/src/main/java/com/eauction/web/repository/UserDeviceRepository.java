package com.eauction.web.repository;

import com.eauction.web.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Integer> {

    Optional<UserDevice> findByUserIdAndDeviceIdAndIsRevokedFalse(Integer userId, String deviceId);

    long countByUserIdAndIsRevokedFalse(Integer userId);
}
