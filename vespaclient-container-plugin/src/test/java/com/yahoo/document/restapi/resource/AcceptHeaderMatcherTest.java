// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author vekterli
 */
public class AcceptHeaderMatcherTest {

    @Test
    void empty_header_prefers_nothing() {
        var m = new AcceptHeaderMatcher("");
        assertEquals(List.of(), m.preferredExactMediaTypes("text/plain"));
    }

    @Test
    void single_header_media_type_can_be_matched() {
        var m = new AcceptHeaderMatcher("text/plain");
        assertEquals(List.of("text/plain"), m.preferredExactMediaTypes("text/plain"));
        assertEquals(List.of("text/plain"), m.preferredExactMediaTypes("image/png", "text/plain", "sausage/wiener"));
        assertEquals(List.of(), m.preferredExactMediaTypes("text/cringe-star-trek-self-insert-fanfics"));
    }

    @Test
    void multiple_header_media_types_can_be_matched() {
        var m = new AcceptHeaderMatcher("text/plain, text/notepad-exe, text/creepypasta, text/copypasta");
        assertEquals(List.of("text/plain"), m.preferredExactMediaTypes("text/plain"));
        assertEquals(List.of("text/notepad-exe"), m.preferredExactMediaTypes("text/notepad-exe"));
        assertEquals(List.of("text/creepypasta", "text/copypasta"),
                m.preferredExactMediaTypes("text/creepypasta", "text/copypasta"));
        assertEquals(List.of("text/plain", "text/notepad-exe", "text/creepypasta", "text/copypasta"),
                m.preferredExactMediaTypes("text/plain", "text/notepad-exe", "text/creepypasta", "text/copypasta"));
        assertEquals(List.of(), m.preferredExactMediaTypes("text/tasty-bread-recipes"));
    }

    @Test
    void media_type_and_subtypes_are_not_wildcard_matched() {
        var m = new AcceptHeaderMatcher("*/*, text/*, */plain");
        assertEquals(List.of(), m.preferredExactMediaTypes("text/plain"));
    }

    @Test
    void prefer_matches_with_highest_quality() {
        // No "q=" implies "q=1", i.e. highest quality.
        var m = new AcceptHeaderMatcher("text/plain;q=0.5, text/html;q=0.8, image/png;q=0.2, burger/whopper");

        assertEquals(List.of("burger/whopper", "text/html", "text/plain", "image/png"),
                m.preferredExactMediaTypes("text/html", "image/png", "text/plain", "burger/whopper"));
        assertEquals(List.of("text/html", "image/png"), m.preferredExactMediaTypes("image/png", "text/html"));
        assertEquals(List.of("text/html"), m.preferredExactMediaTypes("text/html"));
    }

    @Test
    void multiple_matches_with_same_quality_is_returned_in_caller_order() {
        var m = new AcceptHeaderMatcher("foo/bar;q=0.5, bar/baz;q=0.5, zoid/berg;q=0.5, pinky/brain;q=0.5");
        assertEquals(List.of("zoid/berg", "pinky/brain", "foo/bar", "bar/baz"),
                m.preferredExactMediaTypes("zoid/berg", "pinky/brain", "foo/bar", "bar/baz"));
    }

    @Test
    void multiple_entries_for_same_type_use_best_quality() {
        var m = new AcceptHeaderMatcher("image/meme;type=dogs;q=0.7,image/meme;type=cats;q=0.8," +
                                        "image/meme;type=hamsters;q=0.5,image/vangogh;q=0.799");
        assertEquals(List.of("image/meme", "image/vangogh"), m.preferredExactMediaTypes("image/vangogh", "image/meme"));
    }

    @Test
    void matcher_ignores_quoted_string_contents() {
        var m = new AcceptHeaderMatcher("image/gif;foo=\"image/png, so/sneaky;bar=\\\"lol\\\"\";q=0.1");
        assertEquals(List.of("image/gif"), m.preferredExactMediaTypes("image/gif", "image/png", "so/sneaky"));
    }

    // As per RFC 9110 5.6.1.2. Recipient Requirements... :I
    @Test
    void matcher_ignores_silly_empty_list_elements() {
        var m = new AcceptHeaderMatcher(",foo/bar,,, , bar/baz,  , zoid/berg,");
        assertEquals(List.of("foo/bar", "bar/baz", "zoid/berg"),
                m.preferredExactMediaTypes("foo/bar", "bar/baz", "zoid/berg"));
    }

    @Test
    void whitespace_is_ignored_when_applicable() {
        var m = new AcceptHeaderMatcher("foo/bar ;  \tzoid=berg   ;\t q=1  ,  bar/baz  ; q=0.001");
        assertEquals(List.of("foo/bar", "bar/baz"), m.preferredExactMediaTypes("foo/bar", "bar/baz"));
    }

    @Test
    void tokens_can_have_certain_non_alpha_chars() {
        String suspiciousMediaType = "~*^___^*~#$%&/'so|kawaii!!...+`";
        var m = new AcceptHeaderMatcher(suspiciousMediaType);
        assertEquals(List.of(suspiciousMediaType), m.preferredExactMediaTypes(suspiciousMediaType));
    }

    @Test
    void parse_failure_throws_exception() {
        // No spaces around media type components
        assertThrows(IllegalArgumentException.class, () -> new AcceptHeaderMatcher("sausage / currywurst"));
        // Bad quality values
        assertThrows(IllegalArgumentException.class, () -> new AcceptHeaderMatcher("text/plain;q=1.1"));
        assertThrows(IllegalArgumentException.class, () -> new AcceptHeaderMatcher("text/plain;q=-0"));
        assertThrows(IllegalArgumentException.class, () -> new AcceptHeaderMatcher("text/plain;q=0.0001"));
        assertThrows(IllegalArgumentException.class, () -> new AcceptHeaderMatcher("text/plain;q=1.asdf"));
        assertThrows(IllegalArgumentException.class, () -> new AcceptHeaderMatcher("text/plain;q=1.#"));
        // Bad parameters
        assertThrows(IllegalArgumentException.class, () -> new AcceptHeaderMatcher("text/plain;foo = bar"));
        assertThrows(IllegalArgumentException.class, () -> new AcceptHeaderMatcher("text/plain;foo="));
        // Bad string parameters
        assertThrows(IllegalArgumentException.class, () -> new AcceptHeaderMatcher("foo/bar;baz=\""));
        assertThrows(IllegalArgumentException.class, () -> new AcceptHeaderMatcher("foo/bar;baz=\"hmm"));
        assertThrows(IllegalArgumentException.class, () -> new AcceptHeaderMatcher("foo/bar;baz=\"\\"));
        assertThrows(IllegalArgumentException.class, () -> new AcceptHeaderMatcher("foo/bar;baz=\"\\\""));
        // Bad token characters
        assertThrows(IllegalArgumentException.class, () -> new AcceptHeaderMatcher("hæm/børger"));
    }

}
