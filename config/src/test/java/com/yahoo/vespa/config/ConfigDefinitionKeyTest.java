// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests ConfigDefinitionKey
 *
 * @author hmusum
 */
public class ConfigDefinitionKeyTest {

    @Test
    public void testBasic() {
        ConfigDefinitionKey def1 = new ConfigDefinitionKey("foo", "fuz");
        ConfigDefinitionKey def2 = new ConfigDefinitionKey("foo", "bar");

        assertEquals("foo", def1.getName());
        assertEquals("fuz", def1.getNamespace());

        assertTrue(def1.equals(def1));
        assertFalse(def1.equals(def2));
        assertFalse(def1.equals(new Object()));
        assertTrue(def2.equals(def2));
    }

    @Test
    public void testCreationFromConfigKey() {
        ConfigKey<?> key1 = new ConfigKey<>("foo", "id", "bar");
        ConfigDefinitionKey def1 = new ConfigDefinitionKey(key1);

        assertEquals(key1.getName(), def1.getName());
        assertEquals(key1.getNamespace(), def1.getNamespace());
    }

}
