// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.Payload;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Helper class for memory and disk cache unit tests
 *
 * @author hmusum
 * @since 5.1.10
 */
public class CacheTest {
    String defName = "foo";
    String configId = "id";
    String namespace = "bar";
    String defMd5 = "a";

    long generation = 1L;
    String defName2 = "baz-quux";
    String namespace2 = "search.config";
    // Test with a config id with / in it
    String configId2 = "clients/gateways/gateway/component/com.yahoo.feedhandler.VespaFeedHandlerRemoveLocation";
    String defMd52 = "a2";
    String differentDefMd5 = "09ef";
    String configMd5 = "b";
    ConfigKey<?> configKey = new ConfigKey<>(defName, configId, namespace);
    ConfigKey<?> configKey2 = new ConfigKey<>(defName2, configId2, namespace2);
    ConfigCacheKey cacheKey;
    ConfigCacheKey cacheKeyDifferentMd5;
    ConfigCacheKey cacheKey2;
    RawConfig config;
    RawConfig config2;
    RawConfig configDifferentMd5;
    RawConfig unknown;
    Payload payload;
    Payload payload2;
    Payload payloadDifferentMd5;

    public CacheTest() {
    }

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

        config = new RawConfig(configKey, defMd5, payload, configMd5, generation, defContent, Optional.empty());
        config2 = new RawConfig(configKey2, defMd52, payload2, configMd5, generation, defContent, Optional.empty());
        unknown = new RawConfig(new ConfigKey<>("unknown", configId, namespace), defMd5, payload, configMd5, generation, defContent, Optional.empty());
        configDifferentMd5 = new RawConfig(configKey, differentDefMd5, payloadDifferentMd5, configMd5, generation, defContent, Optional.empty());

        cacheKey = new ConfigCacheKey(configKey, config.getDefMd5());
        cacheKey2 = new ConfigCacheKey(configKey2, config2.getDefMd5());
        cacheKeyDifferentMd5 = new ConfigCacheKey(configKey, differentDefMd5);
    }
}