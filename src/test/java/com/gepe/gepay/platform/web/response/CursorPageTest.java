package com.gepe.gepay.platform.web.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CursorPageTest {

    @Test
    void of_trimsExtraRowAndSetsNextCursor() {
        var page = CursorPage.of(List.of("a", "b", "c"), 2, String::toUpperCase);

        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("B");
    }

    @Test
    void of_exactSize_hasNoNext() {
        var page = CursorPage.of(List.of("a", "b"), 2, s -> s);

        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void of_empty_hasNoNext() {
        var page = CursorPage.of(List.<String>of(), 2, s -> s);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void map_keepsPaginationMetadata() {
        var page = CursorPage.of(List.of("a", "b", "c"), 2, String::toUpperCase)
                .map(String::length);

        assertThat(page.items()).containsExactly(1, 1);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("B");
    }
}
