// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.Payload;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author hmusum
 * @since 5.1.9
 */
public class MemoryCacheTest {
    private String defName = "foo";
    private String configId = "id";
    private String namespace = "bar";
    private static final String defMd5 = "a";

    private long generation = 1L;
    private String defName2 = "baz-quux";
    private String namespace2 = "search.config";
    // Test with a config id with / in it
    private String configId2 = "clients/gateways/gateway/component/com.yahoo.feedhandler.VespaFeedHandlerRemoveLocation";
    private static final String defMd52 = "a2";
    private static final String differentDefMd5 = "09ef";
    private static final String configMd5 = "b";
    private ConfigKey<?> configKey = new ConfigKey<>(defName, configId, namespace);
    private ConfigKey<?> configKey2 = new ConfigKey<>(defName2, configId2, namespace2);
    private ConfigCacheKey cacheKey;
    private ConfigCacheKey cacheKeyDifferentMd5;
    private ConfigCacheKey cacheKey2;
    private RawConfig config;
    private RawConfig config2;
    private RawConfig configDifferentMd5;
    private Payload payload;
    private Payload payload2;
    private Payload payloadDifferentMd5;

    @Before
    public void setup() {
        ArrayList<String> defContent = new ArrayList<>();
        defContent.add("bar string");

        Slime slime = new Slime();
        slime.setString("bar \"value\"");
        payload = Payload.from(new ConfigPayload(slime));

        slime = new Slime();
        slime.setString("bar \"baz\"");
        payload2 = Payload.from(new ConfigPayload(slime));

        slime = new Slime();
        slime.setString("bar \"value2\"");
        payloadDifferentMd5 = Payload.from(new ConfigPayload(slime));

        config = new RawConfig(configKey, defMd5, payload, configMd5, generation, false, defContent, Optional.empty());
        config2 = new RawConfig(configKey2, defMd52, payload2, configMd5, generation, false, defContent, Optional.empty());
        configDifferentMd5 = new RawConfig(configKey, differentDefMd5, payloadDifferentMd5, configMd5, generation, false, defContent, Optional.empty());

        cacheKey = new ConfigCacheKey(configKey, config.getDefMd5());
        cacheKey2 = new ConfigCacheKey(configKey2, config2.getDefMd5());
        cacheKeyDifferentMd5 = new ConfigCacheKey(configKey, differentDefMd5);
    }

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
