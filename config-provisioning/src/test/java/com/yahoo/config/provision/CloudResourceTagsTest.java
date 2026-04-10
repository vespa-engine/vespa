// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
class CloudResourceTagsTest {

    @Test
    void empty_tags() {
        CloudResourceTags tags = CloudResourceTags.empty();
        assertTrue(tags.isEmpty());
        assertEquals(0, tags.size());
        assertEquals(Map.of(), tags.asMap());
    }

    @Test
    void from_empty_map_returns_empty() {
        assertEquals(CloudResourceTags.empty(), CloudResourceTags.from(Map.of()));
    }

    @Test
    void basic_construction() {
        var tags = CloudResourceTags.from(Map.of("env", "prod", "team", "search"));
        assertFalse(tags.isEmpty());
        assertEquals(2, tags.size());
        assertEquals("prod", tags.asMap().get("env"));
        assertEquals("search", tags.asMap().get("team"));
    }

    @Test
    void map_is_unmodifiable() {
        var tags = CloudResourceTags.from(Map.of("key", "value"));
        assertThrows(UnsupportedOperationException.class, () -> tags.asMap().put("new", "entry"));
    }

    @Test
    void equals_and_hashcode() {
        var tags1 = CloudResourceTags.from(Map.of("a", "1", "b", "2"));
        var tags2 = CloudResourceTags.from(Map.of("b", "2", "a", "1"));
        assertEquals(tags1, tags2);
        assertEquals(tags1.hashCode(), tags2.hashCode());

        var tags3 = CloudResourceTags.from(Map.of("a", "1", "b", "3"));
        assertNotEquals(tags1, tags3);
    }

    @Test
    void merge_with_empty() {
        var tags = CloudResourceTags.from(Map.of("a", "1"));
        assertEquals(tags, tags.mergedWith(CloudResourceTags.empty()));
        assertEquals(tags, CloudResourceTags.empty().mergedWith(tags));
    }

    @Test
    void merge_combines_tags() {
        var base = CloudResourceTags.from(Map.of("a", "1", "b", "2"));
        var overlay = CloudResourceTags.from(Map.of("b", "override", "c", "3"));
        var merged = base.mergedWith(overlay);

        assertEquals(3, merged.size());
        assertEquals("1", merged.asMap().get("a"));
        assertEquals("override", merged.asMap().get("b"));
        assertEquals("3", merged.asMap().get("c"));
    }

    @Test
    void empty_key_rejected() {
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("", "value")));
    }

    @Test
    void key_exceeding_max_length_rejected() {
        String longKey = "k".repeat(129);
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of(longKey, "value")));
    }

    @Test
    void value_exceeding_max_length_rejected() {
        String longValue = "v".repeat(257);
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(Map.of("key", longValue)));
    }

    @Test
    void too_many_tags_rejected() {
        Map<String, String> tooMany = IntStream.rangeClosed(1, 51)
                                               .boxed()
                                               .collect(toMap(i -> "key" + i, i -> "val" + i));
        assertThrows(IllegalArgumentException.class, () -> CloudResourceTags.from(tooMany));
    }

    @Test
    void max_allowed_tags_accepted() {
        Map<String, String> maxTags = IntStream.rangeClosed(1, 50)
                                               .boxed()
                                               .collect(toMap(i -> "key" + i, i -> "val" + i));
        var tags = CloudResourceTags.from(maxTags);
        assertEquals(50, tags.size());
    }

    @Test
    void empty_value_is_allowed() {
        var tags = CloudResourceTags.from(Map.of("key", ""));
        assertEquals("", tags.asMap().get("key"));
    }

    @Test
    void to_string() {
        var tags = CloudResourceTags.from(Map.of("env", "prod"));
        assertTrue(tags.toString().contains("env"));
        assertTrue(tags.toString().contains("prod"));
    }

}
