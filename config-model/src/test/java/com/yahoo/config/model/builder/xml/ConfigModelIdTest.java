// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.builder.xml;

import com.yahoo.component.Version;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class ConfigModelIdTest {

    @Test
    public void require_that_element_gets_correct_name() {
        ConfigModelId id = ConfigModelId.fromName("foo");
        assertThat(id.getName(), is("foo"));
        assertThat(id.getVersion(), is(Version.fromString("1")));
        id = ConfigModelId.fromNameAndVersion("bar", "2.2");
        assertThat(id.getName(), is("bar"));
        assertThat(id.getVersion(), is(Version.fromString("2.2")));
    }

    @Test
    public void test_toString() {
        ConfigModelId id = ConfigModelId.fromNameAndVersion("bar", "1.0");
        assertThat(id.toString(), is("bar.1"));
        id = ConfigModelId.fromNameAndVersion("foo", "1.1.3");
        assertThat(id.toString(), is("foo.1.1.3"));
        id = ConfigModelId.fromNameAndVersion("bar", "1");
        assertThat(id.toString(), is("bar.1"));
    }

    @Test
    public void test_equality() {
        ConfigModelId a1 = ConfigModelId.fromName("a");
        ConfigModelId a2 = ConfigModelId.fromName("a");
        ConfigModelId b = ConfigModelId.fromName("b");
        assertTrue(a1.equals(a2));
        assertTrue(a2.equals(a1));
        assertFalse(a1.equals(b));
        assertFalse(a2.equals(b));
        assertFalse(b.equals(a1));
        assertFalse(b.equals(a2));
        assertTrue(a1.equals(a1));
        assertTrue(a2.equals(a2));
        assertTrue(b.equals(b));
    }

    @Test
    public void test_compare() {
        ConfigModelId a1 = ConfigModelId.fromName("a");
        ConfigModelId a2 = ConfigModelId.fromName("a");
        ConfigModelId b = ConfigModelId.fromName("b");
        assertTrue(a1.compareTo(a2) == 0);
        assertTrue(a2.compareTo(a1) == 0);
        assertFalse(a1.compareTo(b) > 0);
        assertFalse(a2.compareTo(b) > 0);
        assertFalse(b.compareTo(a1) < 0);
        assertFalse(b.compareTo(a2) < 0);
        assertTrue(a1.compareTo(a1) == 0);
        assertTrue(a2.compareTo(a2) == 0);
        assertTrue(b.compareTo(b) == 0);
    }
}
