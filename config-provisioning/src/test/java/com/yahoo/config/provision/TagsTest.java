// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class TagsTest {

    @Test
    public void testEmpty() {
        assertEquals(Tags.empty(), Tags.fromString(null));
        assertEquals(Tags.empty(), Tags.fromString(""));
        assertEquals(Tags.empty(), Tags.fromString(" "));
    }
    
    @Test
    public void testDeserialization() {
        assertEquals(new Tags(Set.of("tag1", "tag2")), Tags.fromString("  tag1     tag2  "));
    }

    @Test
    public void testSerialization() {
        Tags tags = new Tags(Set.of("a", "tag2", "3"));
        assertEquals(tags, Tags.fromString(tags.toString())); // Required by automatic serialization
        assertEquals(tags, Tags.fromString(tags.asString())); // Required by automatic serialization
    }

    @Test
    public void testContains() {
        Tags tags = new Tags(Set.of("a", "tag2", "3"));
        assertTrue(tags.contains("a"));
        assertTrue(tags.contains("tag2"));
        assertTrue(tags.contains("3"));
        assertFalse(tags.contains("other"));

        Tags subTags = new Tags(Set.of("a", "3"));
        assertTrue(tags.containsAll(subTags));
        assertFalse(subTags.containsAll(tags));
    }

    @Test
    public void testIntersects() {
        Tags tags1 = new Tags(Set.of("a", "tag2", "3"));
        Tags tags2 = new Tags(Set.of("a", "tag3"));
        assertTrue(tags1.intersects(tags2));
        assertTrue(tags2.intersects(tags1));
    }

}
