// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.vespa.config.RawConfig;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author hmusum
 * @since 5.1.9
 */
public class MemoryCacheTest extends CacheTest {

    @Test
    public void basic() {
        MemoryCache cache = new MemoryCache();

        cache.put(config);
        cache.put(config2);
        assertThat(cache.size(), is(2));
        assertTrue(cache.containsKey(cacheKey));
        assertTrue(cache.containsKey(cacheKey2));

        RawConfig response = cache.get(cacheKey);
        assertNotNull(response);
        assertThat(response.getName(), is(defName));
        assertThat(response.getPayload().toString(), is(payload.toString()));
        assertThat(response.getGeneration(), is(generation));

        response = cache.get(cacheKey2);
        assertNotNull(response);
        assertThat(response.getName(), is(defName2));
        assertThat(response.getPayload().toString(), is(payload2.toString()));
        assertThat(response.getGeneration(), is(generation));

        cache.clear();
    }

    @Test
    public void testSameConfigNameDifferentMd5() {
        MemoryCache cache = new MemoryCache();

        cache.put(config);
        cache.put(configDifferentMd5); // same name, different defMd5
        assertThat(cache.size(), is(2));
        assertTrue(cache.containsKey(cacheKey));

        RawConfig response = cache.get(cacheKey);
        assertNotNull(response);
        assertThat(response.getName(), is(defName));
        assertThat(response.getPayload().getData(), is(payload.getData()));
        assertThat(response.getGeneration(), is(generation));

        response = cache.get(cacheKeyDifferentMd5);
        assertNotNull(response);
        assertThat(response.getName(), is(defName));
        assertThat(response.getPayload().getData(), is(payloadDifferentMd5.getData()));
        assertThat(response.getGeneration(), is(generation));

        cache.clear();
        assertThat(cache.size(), is(0));
    }
}
