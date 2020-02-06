// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;

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
        assertThat(k1.hashCode(), is(k2.hashCode()));
        assertThat(k1.getDefMd5(), is(defMd5));
        assertThat(k1.toString(), is(configKey.toString() + "," + defMd5));
        assertThat(k5.hashCode(), is(not(k1.hashCode())));
    }

}
