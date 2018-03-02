// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import org.junit.Test;

import static org.junit.Assert.*;


/**
 * Class to hold config definitions and resolving requests for the correct definition
 *
 * @author hmusum
 */
public class ConfigDefinitionSetTest {

    @Test
    public void testBasic() {
        ConfigDefinitionSet configDefinitionSet = new ConfigDefinitionSet();
        ConfigDefinition def1 = new ConfigDefinition("foo", "1");
        ConfigDefinition def2 = new ConfigDefinition("foo", "1", "namespace1");
        ConfigDefinition def3 = new ConfigDefinition("foo", "1", "namespace2");
        final ConfigDefinitionKey key1 = new ConfigDefinitionKey(def1.getName(), def1.getNamespace());
        configDefinitionSet.add(key1, def1);
        ConfigDefinitionKey key2 = new ConfigDefinitionKey(def2.getName(), def2.getNamespace());
        configDefinitionSet.add(key2, def2);
        ConfigDefinitionKey key3 = new ConfigDefinitionKey(def3.getName(), def3.getNamespace());
        configDefinitionSet.add(key3, def3);
        assertEquals(3, configDefinitionSet.size());
        assertEquals(def1, configDefinitionSet.get(key1));
        assertEquals(def2, configDefinitionSet.get(key2));
        assertEquals(def3, configDefinitionSet.get(key3));

        String str = configDefinitionSet.toString();
        assertTrue(str.contains("namespace1.foo"));
        assertTrue(str.contains("namespace2.foo"));
        assertTrue(str.contains("config.foo"));
    }

    @Test
    public void testFallbackToDefaultNamespace() {
        ConfigDefinitionSet configDefinitionSet = new ConfigDefinitionSet();
        ConfigDefinition def1 = new ConfigDefinition("foo", "1");
        ConfigDefinition def2 = new ConfigDefinition("bar", "1", "namespace");

        configDefinitionSet.add(new ConfigDefinitionKey(def1.getName(), def1.getNamespace()), def1);
        configDefinitionSet.add(new ConfigDefinitionKey(def2.getName(), def2.getNamespace()), def2);

        // fallback to default namespace
        assertEquals(def1, configDefinitionSet.get(new ConfigDefinitionKey("foo", "namespace")));
        // Should not fallback to some other config with same name, but different namespace (not default namespace)
        assertNull(configDefinitionSet.get(new ConfigDefinitionKey("bar", "someothernamespace")));
    }

}
