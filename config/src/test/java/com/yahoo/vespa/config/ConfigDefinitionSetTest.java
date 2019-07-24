// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Class to hold config definitions and resolving requests for the correct definition
 *
 * @author hmusum
 */
public class ConfigDefinitionSetTest {

    @Test
    public void testBasic() {
        ConfigDefinitionSet configDefinitionSet = new ConfigDefinitionSet();
        ConfigDefinition def2 = new ConfigDefinition("foo", "1", "namespace1");
        ConfigDefinition def3 = new ConfigDefinition("foo", "1", "namespace2");
        ConfigDefinitionKey key2 = new ConfigDefinitionKey(def2.getName(), def2.getNamespace());
        configDefinitionSet.add(key2, def2);
        ConfigDefinitionKey key3 = new ConfigDefinitionKey(def3.getName(), def3.getNamespace());
        configDefinitionSet.add(key3, def3);
        assertEquals(2, configDefinitionSet.size());
        assertEquals(def2, configDefinitionSet.get(key2));
        assertEquals(def3, configDefinitionSet.get(key3));

        String str = configDefinitionSet.toString();
        assertTrue(str.contains("namespace1.foo"));
        assertTrue(str.contains("namespace2.foo"));
    }

}
