// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.foo.AppConfig;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 *
 * @author hmusum
 */
public class ConfigKeyTest {

    @Test
    public void testConfigId()  {
        String namespace = "bar";
        ConfigKey<?> key1 = new ConfigKey<>("foo", "a/b/c", namespace);
        ConfigKey<?> key2 = new ConfigKey<>("foo", "a/b/c", namespace);

        assertEquals(key1, key2);

        ConfigKey<?> key3 = new ConfigKey<>("foo", "a/b/c/d", namespace);
        assertNotEquals(key1, key3);

        assertEquals("a/b/c", new ConfigKey<>("foo", "a/b/c", namespace).getConfigId());
        assertEquals("a", new ConfigKey<>("foo", "a", namespace).getConfigId());
        assertEquals("", new ConfigKey<>("foo", "", namespace).getConfigId());

        assertEquals(key1, key1);
        assertNotEquals(key1, key3);
        assertNotEquals(key1, new Object());

        ConfigKey<?> key4 = new ConfigKey<>("myConfig", null, namespace);
        assertEquals("", key4.getConfigId());
    }

    @Test
    public void testConfigKey() {
        String name = AppConfig.CONFIG_DEF_NAME;
        String namespace = AppConfig.CONFIG_DEF_NAMESPACE;
        String md5 = AppConfig.CONFIG_DEF_MD5;
        String configId = "myId";

        ConfigKey<AppConfig> classKey = new ConfigKey<>(AppConfig.class, configId);
        assertEquals("Name is set correctly from class", name, classKey.getName());
        assertEquals("Namespace is set correctly from class", namespace, classKey.getNamespace());
        assertEquals(configId, classKey.getConfigId());

        ConfigKey<?> stringKey = new ConfigKey<>(name, configId, namespace);
        assertEquals("Key created from class equals key created from strings", stringKey, classKey);
    }

    @Test(expected = ConfigurationRuntimeException.class)
    public void testNoName() {
        new ConfigKey<>(null, "", "");
    }

    // Tests namespace and equals with combinations of namespace.
    @Test
    public void testNamespace() {
        ConfigKey<?> namespaceFoo = new ConfigKey<>("name", "id", "foo");
        ConfigKey<?> namespaceBar = new ConfigKey<>("name", "id", "bar");
        assertEquals(namespaceFoo, namespaceFoo);
        assertNotEquals(namespaceFoo, namespaceBar);
        assertEquals(namespaceBar.getNamespace(), "bar");
    }

    @Test
    public void testSorting() {
        ConfigKey<?> k1 = new ConfigKey<>("name3", "id2", "nsc");
        ConfigKey<?> k2 = new ConfigKey<>("name2", "id2", "nsb");
        ConfigKey<?> k3 = new ConfigKey<>("name1", "id2", "nsa");
        List<ConfigKey<?>> keys = new ArrayList<>();
        keys.add(k2);
        keys.add(k1);
        keys.add(k3);
        Collections.sort(keys);
        assertEquals(keys.get(0), k3);
        assertEquals(keys.get(1), k2);
        assertEquals(keys.get(2), k1);

        k1 = new ConfigKey<>("name2", "id2", "nsa");
        k2 = new ConfigKey<>("name3", "id3", "nsa");
        k3 = new ConfigKey<>("name1", "id4", "nsa");
        keys = new ArrayList<>();
        keys.add(k3);
        keys.add(k2);
        keys.add(k1);
        Collections.sort(keys);
        assertEquals(keys.get(0), k3);
        assertEquals(keys.get(1), k1);
        assertEquals(keys.get(2), k2);

        k1 = new ConfigKey<>("name", "idC", "nsa");
        k2 = new ConfigKey<>("name", "idA", "nsa");
        k3 = new ConfigKey<>("name", "idB", "nsa");
        keys = new ArrayList<>();
        keys.add(k1);
        keys.add(k2);
        keys.add(k3);
        Collections.sort(keys);
        assertEquals(keys.get(0), k2);
        assertEquals(keys.get(1), k3);
        assertEquals(keys.get(2), k1);
    }

}
