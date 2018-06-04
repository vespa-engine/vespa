// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.SlimeConfigResponse;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author lulf
 * @since 5.1
 */
public class ServerCacheTest {
    private ServerCache cache;

    private static String defMd5 = "595f44fec1e92a71d3e9e77456ba80d1";
    private static String defMd5_2 = "a2f8edfc965802bf6d44826f9da7e2b0";
    private static String configMd5 = "mymd5";
    private static String configMd5_2 = "mymd5_2";
    private static ConfigDefinition payload = new ConfigDefinition("mypayload", new String[0]);
    private static ConfigDefinition payload_2 = new ConfigDefinition("otherpayload", new String[0]);

    private static ConfigDefinitionKey fooBarDefKey = new ConfigDefinitionKey("foo", "bar");
    private static ConfigDefinitionKey fooBazDefKey = new ConfigDefinitionKey("foo", "baz");
    private static ConfigDefinitionKey fooBimDefKey = new ConfigDefinitionKey("foo", "bim");

    private static ConfigKey<?> fooConfigKey = new ConfigKey<>("foo", "id", "bar");
    private static ConfigKey<?> bazConfigKey = new ConfigKey<>("foo", "id2", "bar");

    ConfigCacheKey fooBarCacheKey = new ConfigCacheKey(fooConfigKey, defMd5);
    ConfigCacheKey bazQuuxCacheKey = new ConfigCacheKey(bazConfigKey, defMd5);
    ConfigCacheKey fooBarCacheKeyDifferentMd5 = new ConfigCacheKey(fooConfigKey, defMd5_2);

    @Before
    public void setup() {
        cache = new ServerCache();

        cache.addDef(fooBarDefKey, payload);
        cache.addDef(fooBazDefKey, new com.yahoo.vespa.config.buildergen.ConfigDefinition("baz", new String[0]));

        cache.addDef(fooBimDefKey, new ConfigDefinition("mynode", new String[0]));

        cache.put(fooBarCacheKey, SlimeConfigResponse.fromConfigPayload(ConfigPayload.empty(), payload.getCNode(), 2, false, configMd5), configMd5);
        cache.put(bazQuuxCacheKey, SlimeConfigResponse.fromConfigPayload(ConfigPayload.empty(), payload.getCNode(), 2, false, configMd5), configMd5);
        cache.put(fooBarCacheKeyDifferentMd5, SlimeConfigResponse.fromConfigPayload(ConfigPayload.empty(), payload_2.getCNode(), 2, false, configMd5_2), configMd5_2);
    }

    @Test
    public void testThatCacheWorks() {
        assertNotNull(cache.getDef(fooBazDefKey));
        assertThat(cache.getDef(fooBarDefKey), is(payload));
        assertThat(cache.getDef(fooBimDefKey).getCNode().getName(), is("mynode"));
        ConfigResponse raw = cache.get(fooBarCacheKey);
        assertThat(raw.getConfigMd5(), is(configMd5));
    }

    @Test
    public void testThatCacheWorksWithSameKeyDifferentMd5() {
        assertThat(cache.getDef(fooBarDefKey), is(payload));
        ConfigResponse raw = cache.get(fooBarCacheKey);
        assertThat(raw.getConfigMd5(), is(configMd5));
        raw = cache.get(fooBarCacheKeyDifferentMd5);
        assertThat(raw.getConfigMd5(), is(configMd5_2));
    }

    @Test
    public void testThatCacheWorksWithDifferentKeySameMd5() {
        assertTrue(cache.get(fooBarCacheKey) == cache.get(bazQuuxCacheKey));
    }
}
