// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.vespa.config.*;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.protocol.Payload;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 */
public class ProxyServerTest {

    private final MemoryCache memoryCache = new MemoryCache();
    private final MockConfigSource source = new MockConfigSource();
    private final MockConfigSourceClient client = new MockConfigSourceClient(source, memoryCache);
    private ProxyServer proxy;

    static final RawConfig fooConfig = ConfigTester.fooConfig;

    // errorConfig based on fooConfig
    private static final ConfigKey<?> errorConfigKey = new ConfigKey<>("error", fooConfig.getConfigId(), fooConfig.getNamespace());
    static final RawConfig errorConfig = new RawConfig(errorConfigKey, fooConfig.getDefMd5(), fooConfig.getPayload(),
                                                       fooConfig.getConfigMd5(), fooConfig.getGeneration(), false,
                                                       ErrorCode.UNKNOWN_DEFINITION, fooConfig.getDefContent(), Optional.empty());

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() {
        source.clear();
        source.put(fooConfig.getKey(), createConfigWithNextConfigGeneration(fooConfig, 0));
        source.put(errorConfigKey, createConfigWithNextConfigGeneration(fooConfig, ErrorCode.UNKNOWN_DEFINITION));
        proxy = createTestServer(source, client, memoryCache);
    }

    @After
    public void shutdown() {
        proxy.stop();
    }

    @Test
    public void basic() {
        assertTrue(proxy.getMode().isDefault());
        assertThat(proxy.getMemoryCache().size(), is(0));

        ConfigTester tester = new ConfigTester();
        final MemoryCache memoryCache = proxy.getMemoryCache();
        assertEquals(0, memoryCache.size());
        RawConfig res = proxy.resolveConfig(tester.createRequest(fooConfig));
        assertNotNull(res);
        assertThat(res.getPayload().toString(), is(ConfigTester.fooPayload.toString()));
        assertEquals(1, memoryCache.size());
        assertThat(memoryCache.get(new ConfigCacheKey(fooConfig.getKey(), fooConfig.getDefMd5())), is(res));
    }

    /**
     * Tests that the proxy server RPC commands for setting and getting mode works..
     */
    @Test
    public void testModeSwitch() {
        ProxyServer proxy = createTestServer(source, client, new MemoryCache());
        assertTrue(proxy.getMode().isDefault());

        for (String mode : Mode.modes()) {
            proxy.setMode(mode);
            assertThat(proxy.getMode().name(), is(mode));
        }

        // Try setting an invalid mode
        try {
            proxy.setMode("invalid");
            assert (false);
        } catch (IllegalArgumentException e) {
            assertEquals("Unrecognized mode 'invalid' supplied. Legal modes are '" + Mode.modes() + "'", e.getMessage());
        }

        // Also switch to DEFAULT mode, as that is not covered above
        proxy.setMode("default");
        assertTrue(proxy.getMode().isDefault());

        // Set mode to the same as the current mode
        proxy.setMode(proxy.getMode().name());
        assertTrue(proxy.getMode().isDefault());

        proxy.stop();
    }

    /**
     * Verifies that config is retrieved from the real server when it is not found in the cache,
     * that the cache is populated with the config and that the entry in the cache is used
     * when it is found there.
     */
    @Test
    public void testGetConfigAndCaching() {
        ConfigTester tester = new ConfigTester();
        final MemoryCache memoryCache = proxy.getMemoryCache();
        assertEquals(0, memoryCache.size());
        RawConfig res = proxy.resolveConfig(tester.createRequest(fooConfig));
        assertNotNull(res);
        assertThat(res.getPayload().toString(), is(ConfigTester.fooPayload.toString()));
        assertEquals(1, memoryCache.size());
        assertThat(memoryCache.get(new ConfigCacheKey(fooConfig.getKey(), fooConfig.getDefMd5())), is(res));

        // Trying same config again
        JRTServerConfigRequest newRequestBasedOnResponse = tester.createRequest(res);
        RawConfig res2 = proxy.resolveConfig(newRequestBasedOnResponse);
        assertFalse(ProxyServer.configOrGenerationHasChanged(res2, newRequestBasedOnResponse));
        assertEquals(1, memoryCache.size());
    }

    /**
     * Verifies that error responses are not cached. When the config has been successfully retrieved,
     * it must be updated in cache.
     */
    @Test
    public void testNoCachingOfErrorRequests() {
        ConfigTester tester = new ConfigTester();
        // Simulate an error response
        source.put(fooConfig.getKey(), createConfigWithNextConfigGeneration(fooConfig, ErrorCode.INTERNAL_ERROR));

        final MemoryCache cacheManager = proxy.getMemoryCache();
        assertEquals(0, cacheManager.size());

        RawConfig res = proxy.resolveConfig(tester.createRequest(fooConfig));
        assertNotNull(res);
        assertNotNull(res.getPayload());
        assertTrue(res.isError());
        assertEquals(0, cacheManager.size());

        // Put a version of the same config into backend without error and see that it now works (i.e. we are
        // not getting a cached response (of the error in the previous request)
        source.put(fooConfig.getKey(), createConfigWithNextConfigGeneration(fooConfig, 0));

        // Verify that we get the config now and that it is cached
        res = proxy.resolveConfig(tester.createRequest(fooConfig));
        assertNotNull(res);
        assertNotNull(res.getPayload().getData());
        assertThat(res.getPayload().toString(), is(ConfigTester.fooPayload.toString()));
        assertEquals(1, cacheManager.size());

        JRTServerConfigRequest newRequestBasedOnResponse = tester.createRequest(res);
        RawConfig res2 = proxy.resolveConfig(newRequestBasedOnResponse);
        assertFalse(ProxyServer.configOrGenerationHasChanged(res2, newRequestBasedOnResponse));
        assertEquals(1, cacheManager.size());
    }

    /**
     * Verifies that config with generation 0 (used for empty sentinel config) is not cached.
     * If it was cached, it would be served even when newer config is available
     * (as they ask for config, saying that it now has config with generation 0).
     * When the config has been successfully retrieved it must be updated in cache.
     */
    @Test
    public void testNoCachingOfEmptyConfig() {
        ConfigTester tester = new ConfigTester();
        MemoryCache cache = proxy.getMemoryCache();

        assertEquals(0, cache.size());
        RawConfig res = proxy.resolveConfig(tester.createRequest(fooConfig));
        assertNotNull(res);
        assertThat(res.getPayload().toString(), is(ConfigTester.fooPayload.toString()));
        assertEquals(1, cache.size());

        // Simulate an empty response
        RawConfig emptyConfig = new RawConfig(fooConfig.getKey(), fooConfig.getDefMd5(), Payload.from("{}"),
                                              fooConfig.getConfigMd5(), 0, false,
                                              0, fooConfig.getDefContent(), Optional.empty());
        source.put(fooConfig.getKey(), emptyConfig);

        res = proxy.resolveConfig(tester.createRequest(fooConfig));
        assertNotNull(res.getPayload());
        assertThat(res.getPayload().toString(), is(emptyConfig.getPayload().toString()));
        assertEquals(0, cache.size());

        // Put a version of the same config into backend with new generation and see that it now works (i.e. we are
        // not getting a cached response (of the error in the previous request)
        source.put(fooConfig.getKey(), createConfigWithNextConfigGeneration(fooConfig, 0));

        // Verify that we get the config now and that it is cached
        res = proxy.resolveConfig(tester.createRequest(fooConfig));
        assertNotNull(res.getPayload().getData());
        assertThat(res.getPayload().toString(), is(ConfigTester.fooPayload.toString()));
        assertEquals(1, cache.size());
    }

    @Test
    public void testReconfiguration() {
        ConfigTester tester = new ConfigTester();
        RawConfig res = proxy.resolveConfig(tester.createRequest(fooConfig));
        assertNotNull(res);
        assertThat(res.getPayload().toString(), is(ConfigTester.fooPayload.toString()));

        // Simulate deployment, add config with new config generation
        long previousGeneration = res.getGeneration();
        source.put(fooConfig.getKey(), createConfigWithNextConfigGeneration(res, 0));
        JRTServerConfigRequest newRequestBasedOnResponse = tester.createRequest(res);
        RawConfig res2 = proxy.resolveConfig(newRequestBasedOnResponse);
        assertEquals(previousGeneration + 1, res2.getGeneration());
        assertTrue(ProxyServer.configOrGenerationHasChanged(res2, newRequestBasedOnResponse));
    }

    @Test
    public void testReadingSystemProperties() {
        ProxyServer.Properties properties = ProxyServer.getSystemProperties();
        assertThat(properties.configSources.length, is(1));
        assertThat(properties.configSources[0], is(ProxyServer.DEFAULT_PROXY_CONFIG_SOURCES));
    }

    private static ProxyServer createTestServer(ConfigSourceSet source,
                                                ConfigSourceClient configSourceClient,
                                                MemoryCache memoryCache) {
        return new ProxyServer(null, source, memoryCache, configSourceClient);
    }

    static RawConfig createConfigWithNextConfigGeneration(RawConfig config, int errorCode) {
        return createConfigWithNextConfigGeneration(config, errorCode, config.getPayload());
    }

    private static RawConfig createConfigWithNextConfigGeneration(RawConfig config, int errorCode, Payload payload) {
        return createConfigWithNextConfigGeneration(config, errorCode, payload, config.getGeneration() + 1);
    }

    static RawConfig createConfigWithNextConfigGeneration(RawConfig config, int errorCode, Payload payload, long configGeneration) {
        return new RawConfig(config.getKey(), config.getDefMd5(),
                             payload, config.getConfigMd5(),
                             configGeneration, false,
                             errorCode, config.getDefContent(), Optional.empty());
    }

}
