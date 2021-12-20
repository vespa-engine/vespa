// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 *
 * @author hmusum
 */
public class ConfigCacheKeyTest {

    @Test
    public void testConfigCacheKey() {
        final String defMd5 = "md5";
        final String defMd5_2 = "md5_2";

        ConfigCacheKey k1 = new ConfigCacheKey("foo", "id", "ns", defMd5);
        ConfigCacheKey k2 = new ConfigCacheKey("foo", "id", "ns", defMd5);
        ConfigCacheKey k3 = new ConfigCacheKey("foo", "id", "ns", defMd5_2);
        ConfigCacheKey k4 = new ConfigCacheKey("foo", "id", "ns_1", defMd5);
        ConfigCacheKey k5 = new ConfigCacheKey("foo", "id", "ns_1", null); // test with null defMd5
        final ConfigKey<?> configKey = new ConfigKey<>("foo", "id", "ns");
        ConfigCacheKey k1_2 = new ConfigCacheKey(configKey, defMd5);
        assertEquals(k1, k1);
        assertEquals(k1, k1_2);
        assertEquals(k1, k2);
        assertNotEquals(k3, k2);
        assertNotEquals(k4, k1);
        assertEquals(k2.hashCode(), k1.hashCode());
        assertEquals(defMd5, k1.getDefMd5());
        assertEquals(configKey + "," + defMd5, k1.toString());
        assertNotEquals(k1.hashCode(), k5.hashCode());
    }

}
