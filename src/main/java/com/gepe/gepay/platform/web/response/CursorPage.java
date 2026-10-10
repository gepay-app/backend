package com.gepe.gepay.platform.web.response;

import java.util.List;
import java.util.function.Function;

/**
 * Envelope paginasi berbasis <strong>keyset/cursor</strong> (bukan offset),
 * dibungkus {@link ApiResponse} pada {@code data}. Dirancang untuk list
 * ber-volume besar: tanpa {@code COUNT} dan tanpa {@code OFFSET}, jadi biaya
 * per halaman konstan (index range scan), sedalam apa pun posisinya.
 *
 * <p>Alur klien: panggil tanpa {@code cursor} untuk halaman pertama, lalu
 * {@code ?cursor=<nextCursor>} sampai {@code hasNext=false}.
 *
 * @param items      baris halaman ini (maksimal {@code size})
 * @param hasNext    masih ada halaman berikutnya
 * @param nextCursor cursor opak untuk halaman berikutnya; {@code null} bila habis
 */
public record CursorPage<T>(
        List<T> items,
        boolean hasNext,
        String nextCursor
) {

    /**
     * Bentuk halaman dari hasil query yang mengambil {@code size + 1} baris
     * (baris ekstra dipakai untuk menandai {@code hasNext}).
     *
     * @param fetched  baris hasil query (maksimal {@code size + 1})
     * @param size     ukuran halaman yang diminta
     * @param cursorFn ekstraktor cursor dari elemen (mis. {@code e -> e.getId().toString()})
     */
    public static <T> CursorPage<T> of(List<T> fetched, int size, Function<T, String> cursorFn) {
        boolean hasNext = fetched.size() > size;
        List<T> items = hasNext
                ? List.copyOf(fetched.subList(0, size))
                : List.copyOf(fetched);
        String nextCursor = hasNext ? cursorFn.apply(items.get(items.size() - 1)) : null;
        return new CursorPage<>(items, hasNext, nextCursor);
    }

    /** Petakan elemen ke tipe lain tanpa mengubah metadata paginasi. */
    public <R> CursorPage<R> map(Function<T, R> mapper) {
        return new CursorPage<>(items.stream().map(mapper).toList(), hasNext, nextCursor);
    }
}
