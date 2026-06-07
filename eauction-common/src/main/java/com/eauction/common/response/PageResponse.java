package com.eauction.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final List<T> data;
    private final PageMeta meta;

    @Builder.Default
    private final Instant timestamp = Instant.now();

    @Getter
    @Builder
    public static class PageMeta {
        private final int page;
        private final int size;
        private final long totalElements;
        private final int totalPages;
        private final boolean first;
        private final boolean last;
    }

    public static <T> ResponseEntity<PageResponse<T>> of(Page<T> page) {
        return ResponseEntity.ok(
                PageResponse.<T>builder()
                        .success(true)
                        .code(ResponseCode.SUCCESS.getCode())
                        .message(ResponseCode.SUCCESS.getMessage())
                        .data(page.getContent())
                        .meta(PageMeta.builder()
                                .page(page.getNumber())
                                .size(page.getSize())
                                .totalElements(page.getTotalElements())
                                .totalPages(page.getTotalPages())
                                .first(page.isFirst())
                                .last(page.isLast())
                                .build())
                        .build()
        );
    }

    public static <T> ResponseEntity<PageResponse<T>> of(Page<?> page, List<T> mappedContent) {
        return ResponseEntity.ok(
                PageResponse.<T>builder()
                        .success(true)
                        .code(ResponseCode.SUCCESS.getCode())
                        .message(ResponseCode.SUCCESS.getMessage())
                        .data(mappedContent)
                        .meta(PageMeta.builder()
                                .page(page.getNumber())
                                .size(page.getSize())
                                .totalElements(page.getTotalElements())
                                .totalPages(page.getTotalPages())
                                .first(page.isFirst())
                                .last(page.isLast())
                                .build())
                        .build()
        );
    }
}
