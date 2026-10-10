package com.gepe.gepay.platform.web.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envelope paginasi standar untuk endpoint list (dibungkus {@link ApiResponse}
 * pada {@code data}). {@code page} 0-based agar cocok dengan Spring Data.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    /** Konversi {@link Page} → {@code PageResponse} tanpa mengubah isi. */
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }

    /** Konversi {@link Page} sambil memetakan tiap elemen ke tipe lain. */
    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }

    /** Petakan isi halaman ke tipe lain tanpa mengubah metadata paginasi. */
    public <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(
                items.stream().map(mapper).toList(),
                page,
                size,
                totalElements,
                totalPages,
                hasNext
        );
    }
}
