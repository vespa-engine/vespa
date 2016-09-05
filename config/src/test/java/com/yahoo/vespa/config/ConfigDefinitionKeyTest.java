// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;


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

        assertThat(def1.getName(), is("foo"));
        assertThat(def1.getNamespace(), is("fuz"));

        assertTrue(def1.equals(def1));
        assertFalse(def1.equals(def2));
        assertFalse(def1.equals(new Object()));
        assertTrue(def2.equals(def2));
    }

    @Test
    public void testCreationFromConfigKey() {
        ConfigKey<?> key1 = new ConfigKey<>("foo", "id", "bar");
        ConfigDefinitionKey def1 = new ConfigDefinitionKey(key1);

        assertThat(def1.getName(), is(key1.getName()));
        assertThat(def1.getNamespace(), is(key1.getNamespace()));
    }

}
