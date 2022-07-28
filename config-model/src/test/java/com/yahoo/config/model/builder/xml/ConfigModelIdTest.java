// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.builder.xml;

import com.yahoo.component.Version;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class ConfigModelIdTest {

    @Test
    void require_that_element_gets_correct_name() {
        ConfigModelId id = ConfigModelId.fromName("foo");
        assertEquals("foo", id.getName());
        assertEquals(Version.fromString("1"), id.getVersion());
        id = ConfigModelId.fromNameAndVersion("bar", "2.2");
        assertEquals("bar", id.getName());
        assertEquals(Version.fromString("2.2"), id.getVersion());
    }

    @Test
    void test_toString() {
        ConfigModelId id = ConfigModelId.fromNameAndVersion("bar", "1.0");
        assertEquals("bar.1", id.toString());
        id = ConfigModelId.fromNameAndVersion("foo", "1.1.3");
        assertEquals("foo.1.1.3", id.toString());
        id = ConfigModelId.fromNameAndVersion("bar", "1");
        assertEquals("bar.1", id.toString());
    }

    @Test
    void test_equality() {
        ConfigModelId a1 = ConfigModelId.fromName("a");
        ConfigModelId a2 = ConfigModelId.fromName("a");
        ConfigModelId b = ConfigModelId.fromName("b");
        assertEquals(a1, a2);
        assertEquals(a2, a1);
        assertNotEquals(a1, b);
        assertNotEquals(a2, b);
        assertNotEquals(b, a1);
        assertNotEquals(b, a2);
        assertEquals(a1, a1);
        assertEquals(a2, a2);
        assertEquals(b, b);
    }

    @Test
    void test_compare() {
        ConfigModelId a1 = ConfigModelId.fromName("a");
        ConfigModelId a2 = ConfigModelId.fromName("a");
        ConfigModelId b = ConfigModelId.fromName("b");
        assertEquals(a1.compareTo(a2), 0);
        assertEquals(a2.compareTo(a1), 0);
        assertFalse(a1.compareTo(b) > 0);
        assertFalse(a2.compareTo(b) > 0);
        assertFalse(b.compareTo(a1) < 0);
        assertFalse(b.compareTo(a2) < 0);
        assertEquals(a1.compareTo(a1), 0);
        assertEquals(a2.compareTo(a2), 0);
        assertEquals(b.compareTo(b), 0);
    }
}
