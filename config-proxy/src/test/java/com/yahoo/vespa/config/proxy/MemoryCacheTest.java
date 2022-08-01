// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.PayloadChecksums;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.Payload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hmusum
 */
public class MemoryCacheTest {

    private final String defName = "foo";
    private final String configId = "id";
    private final String namespace = "bar";
    private static final String defMd5 = "a";

    private final long generation = 1L;
    private final String defName2 = "baz-quux";
    private final String namespace2 = "search.config";
    // Test with a config id with / in it
    private final String configId2 = "clients/gateways/gateway/component/com.yahoo.feedhandler.VespaFeedHandlerRemoveLocation";
    private static final String defMd52 = "a2";
    private static final String differentDefMd5 = "09ef";
    private static final PayloadChecksums checksums = PayloadChecksums.from("b", "");
    private final ConfigKey<?> configKey = new ConfigKey<>(defName, configId, namespace);
    private final ConfigKey<?> configKey2 = new ConfigKey<>(defName2, configId2, namespace2);
    private ConfigCacheKey cacheKey;
    private ConfigCacheKey cacheKeyDifferentMd5;
    private ConfigCacheKey cacheKey2;
    private RawConfig config;
    private RawConfig config2;
    private RawConfig configDifferentMd5;
    private Payload payload;
    private Payload payload2;
    private Payload payloadDifferentMd5;

    @BeforeEach
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

        config = new RawConfig(configKey, defMd5, payload, checksums, generation, false, defContent, Optional.empty());
        config2 = new RawConfig(configKey2, defMd52, payload2, checksums, generation, false, defContent, Optional.empty());
        configDifferentMd5 = new RawConfig(configKey, differentDefMd5, payloadDifferentMd5, checksums, generation, false, defContent, Optional.empty());

        cacheKey = new ConfigCacheKey(configKey, config.getDefMd5());
        cacheKey2 = new ConfigCacheKey(configKey2, config2.getDefMd5());
        cacheKeyDifferentMd5 = new ConfigCacheKey(configKey, differentDefMd5);
    }

    @Test
    void basic() {
        MemoryCache cache = new MemoryCache();

        cache.update(config);
        cache.update(config2);
        assertEquals(2, cache.size());
        assertTrue(cache.containsKey(cacheKey));
        assertTrue(cache.containsKey(cacheKey2));

        RawConfig response = cache.get(cacheKey).orElseThrow();
        assertEquals(defName, response.getName());
        assertEquals(payload.toString(), response.getPayload().toString());
        assertEquals(generation, response.getGeneration());

        response = cache.get(cacheKey2).orElseThrow();
        assertEquals(defName2, response.getName());
        assertEquals(payload2.toString(), response.getPayload().toString());
        assertEquals(generation, response.getGeneration());

        cache.clear();
    }

    @Test
    void testSameConfigNameDifferentMd5() {
        MemoryCache cache = new MemoryCache();

        cache.update(config);
        cache.update(configDifferentMd5); // same name, different defMd5
        assertEquals(2, cache.size());
        assertTrue(cache.containsKey(cacheKey));

        RawConfig response = cache.get(cacheKey).orElseThrow();
        assertEquals(defName, response.getName());
        assertEquals(payload.getData(), response.getPayload().getData());
        assertEquals(generation, response.getGeneration());

        response = cache.get(cacheKeyDifferentMd5).orElseThrow();
        assertEquals(defName, response.getName());
        assertEquals(payloadDifferentMd5.getData(), response.getPayload().getData());
        assertEquals(generation, response.getGeneration());

        cache.clear();
        assertEquals(0, cache.size());
    }
}
