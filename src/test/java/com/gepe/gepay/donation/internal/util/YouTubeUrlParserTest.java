package com.gepe.gepay.donation.internal.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class YouTubeUrlParserTest {

    private static final String ID = "dQw4w9WgXcQ";

    @Test
    void acceptsSupportedYoutubeForms() {
        assertVideoId("https://www.youtube.com/watch?v=" + ID);
        assertVideoId("https://youtube.com/watch?v=" + ID + "&t=30s");
        assertVideoId("https://youtu.be/" + ID);
        assertVideoId("https://youtu.be/" + ID + "?t=10");
        assertVideoId("https://www.youtube.com/embed/" + ID);
        assertVideoId("https://www.youtube.com/shorts/" + ID);
        assertVideoId("https://m.youtube.com/watch?v=" + ID);
        assertVideoId("https://music.youtube.com/watch?v=" + ID);
        assertVideoId("https://www.youtube.com/live/" + ID);
    }

    @Test
    void normalizesToCanonicalWatchUrl() {
        Optional<YouTubeUrlParser.Result> result = YouTubeUrlParser.parse("https://youtu.be/" + ID);
        assertThat(result).isPresent();
        assertThat(result.get().canonicalUrl()).isEqualTo("https://www.youtube.com/watch?v=" + ID);
    }

    @Test
    void rejectsNonYoutubeHosts() {
        assertThat(YouTubeUrlParser.parse("https://vimeo.com/" + ID)).isEmpty();
        assertThat(YouTubeUrlParser.parse("https://youtube.com.evil.com/watch?v=" + ID)).isEmpty();
        assertThat(YouTubeUrlParser.parse("https://evil.com/youtube.com/watch?v=" + ID)).isEmpty();
    }

    @Test
    void rejectsInvalidOrMissingIds() {
        assertThat(YouTubeUrlParser.parse("https://www.youtube.com/watch?v=short")).isEmpty();
        assertThat(YouTubeUrlParser.parse("https://www.youtube.com/watch?t=10")).isEmpty();
        assertThat(YouTubeUrlParser.parse("https://youtu.be/")).isEmpty();
        assertThat(YouTubeUrlParser.parse("not a url")).isEmpty();
        assertThat(YouTubeUrlParser.parse("")).isEmpty();
        assertThat(YouTubeUrlParser.parse(null)).isEmpty();
    }

    private void assertVideoId(String url) {
        Optional<YouTubeUrlParser.Result> result = YouTubeUrlParser.parse(url);
        assertThat(result).as(url).isPresent();
        assertThat(result.get().videoId()).as(url).isEqualTo(ID);
    }
}
