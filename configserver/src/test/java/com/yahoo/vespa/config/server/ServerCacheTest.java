// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.PayloadChecksums;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.SlimeConfigResponse;
import org.junit.Before;
import org.junit.Test;

import static com.yahoo.vespa.config.PayloadChecksum.Type.XXHASH64;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * @author Ulf Lilleengen
 */
public class ServerCacheTest {
    private ServerCache cache;

    private static final String defMd5 = "595f44fec1e92a71d3e9e77456ba80d1";
    private static final String defMd5_2 = "a2f8edfc965802bf6d44826f9da7e2b0";
    private static final String xxhash64 = "595f44fec1e92a71d";
    private static final String xxhash64_2 = "a2f8edfc965802bf6";
    private static final ConfigDefinition def = new ConfigDefinition("mypayload", new String[0]);

    private static final ConfigDefinitionKey fooBarDefKey = new ConfigDefinitionKey("foo", "bar");
    private static final ConfigDefinitionKey fooBazDefKey = new ConfigDefinitionKey("foo", "baz");
    private static final ConfigDefinitionKey fooBimDefKey = new ConfigDefinitionKey("foo", "bim");

    private static final ConfigKey<?> fooConfigKey = new ConfigKey<>("foo", "id", "bar");
    private static final ConfigKey<?> bazConfigKey = new ConfigKey<>("foo", "id2", "bar");

    private final ConfigCacheKey fooBarCacheKey = new ConfigCacheKey(fooConfigKey, defMd5);
    private final ConfigCacheKey bazQuuxCacheKey = new ConfigCacheKey(bazConfigKey, defMd5);
    private final ConfigCacheKey fooBarCacheKeyDifferentMd5 = new ConfigCacheKey(fooConfigKey, defMd5_2);

    @Before
    public void setup() {
        UserConfigDefinitionRepo userConfigDefinitionRepo = new UserConfigDefinitionRepo();
        userConfigDefinitionRepo.add(fooBarDefKey, def);
        userConfigDefinitionRepo.add(fooBazDefKey, new com.yahoo.vespa.config.buildergen.ConfigDefinition("baz", new String[0]));
        userConfigDefinitionRepo.add(fooBimDefKey, new ConfigDefinition("mynode", new String[0]));

        cache = new ServerCache(new TestConfigDefinitionRepo(), userConfigDefinitionRepo);

        cache.computeIfAbsent(fooBarCacheKey, (ConfigCacheKey key) -> createResponse(xxhash64));
        cache.computeIfAbsent(bazQuuxCacheKey, (ConfigCacheKey key) -> createResponse(xxhash64));
        cache.computeIfAbsent(fooBarCacheKeyDifferentMd5, (ConfigCacheKey key) -> createResponse(xxhash64_2));
    }

    @Test
    public void testThatCacheWorks() {
        assertNotNull(cache.getDef(fooBazDefKey));
        assertEquals(def, cache.getDef(fooBarDefKey));
        assertEquals("mynode", cache.getDef(fooBimDefKey).getCNode().getName());
        ConfigResponse raw = cache.get(fooBarCacheKey);
        assertEquals(xxhash64, raw.getPayloadChecksums().getForType(XXHASH64).asString());
    }

    @Test
    public void testThatCacheWorksWithSameKeyDifferentMd5() {
        assertEquals(def, cache.getDef(fooBarDefKey));
        ConfigResponse raw = cache.get(fooBarCacheKey);
        assertEquals(xxhash64, raw.getPayloadChecksums().getForType(XXHASH64).asString());
        raw = cache.get(fooBarCacheKeyDifferentMd5);
        assertEquals(xxhash64_2, raw.getPayloadChecksums().getForType(XXHASH64).asString());
    }

    @Test
    public void testThatCacheWorksWithDifferentKeySameMd5() {
        assertSame(cache.get(fooBarCacheKey), cache.get(bazQuuxCacheKey));
    }

    SlimeConfigResponse createResponse(String xxhash64) {
        return SlimeConfigResponse.fromConfigPayload(ConfigPayload.empty(), 2, false,
                                                     PayloadChecksums.from("", xxhash64));
    }

}
