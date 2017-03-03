// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.vespa.config.*;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author hmusum
 * @since 5.1.9
 */
public class ProxyServerTest {

    private final MapBackedConfigSource source = new MapBackedConfigSource(UpstreamConfigSubscriberTest.MockClientUpdater.create());
    private ProxyServer proxy = ProxyServer.createTestServer(source);

    static final RawConfig fooConfig = Helper.fooConfigV2;

    // errorConfig based on fooConfig
    private static final ConfigKey<?> errorConfigKey = new ConfigKey<>("error", fooConfig.getConfigId(), fooConfig.getNamespace());
    static final RawConfig errorConfig = new RawConfig(errorConfigKey, fooConfig.getDefMd5(),
            fooConfig.getPayload(), fooConfig.getConfigMd5(),
            fooConfig.getGeneration(), ErrorCode.UNKNOWN_DEFINITION, fooConfig.getDefContent(), Optional.empty());

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() {
        source.clear();
        source.put(fooConfig.getKey(), createConfigWithNextConfigGeneration(fooConfig, 0));
        source.put(errorConfigKey, createConfigWithNextConfigGeneration(fooConfig, ErrorCode.UNKNOWN_DEFINITION));
        proxy = ProxyServer.createTestServer(source);
    }

    @After
    public void shutdown() {
        proxy.stop();
    }

    @Test
    public void basic() {
        assertTrue(proxy.getMode().isDefault());
        assertThat(proxy.getMemoryCache().size(), is(0));
        assertThat(proxy.getTimingValues(), is(ProxyServer.defaultTimingValues()));
    }

    /**
     * Tests that the proxy server RPC commands for setting and getting mode works..
     */
    @Test
    public void testModeSwitch() {
        ConfigSourceSet source = new ConfigSourceSet(); // Need to use a ConfigSourceSet to test modes
        ProxyServer proxy = ProxyServer.createTestServer(source);
        assertTrue(proxy.getMode().isDefault());

        for (String mode : Mode.modes()) {
            proxy.setMode(mode);
            assertThat(proxy.getMode().name(), is(mode));
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
     * Tests that the proxy server can be tested with a MapBackedConfigSource,
     * which is a simple hash map with configs
     */
    @Test
    public void testRawConfigSetBasics() {
        ConfigTester tester = new ConfigTester();
        JRTServerConfigRequest errorConfigRequest = tester.createRequest(errorConfig);

        assertTrue(proxy.getMode().isDefault());
        RawConfig config = proxy.resolveConfig(Helper.fooConfigRequest);
        assertThat(config, is(createConfigWithNextConfigGeneration(Helper.fooConfig, 0)));

        config = proxy.resolveConfig(Helper.barConfigRequest);
        assertNull(config);

        config = proxy.resolveConfig(errorConfigRequest);
        assertThat(config.errorCode(), is(ErrorCode.UNKNOWN_DEFINITION));
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
        assertThat(res.getPayload().toString(), is(Helper.fooConfigPayload.toString()));
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
     * it must be put in the cache.
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
        assertThat(res.getPayload().toString(), is(Helper.fooConfigPayload.toString()));
        assertEquals(1, cacheManager.size());

        JRTServerConfigRequest newRequestBasedOnResponse = tester.createRequest(res);
        RawConfig res2 = proxy.resolveConfig(newRequestBasedOnResponse);
        assertFalse(ProxyServer.configOrGenerationHasChanged(res2, newRequestBasedOnResponse));
        assertEquals(1, cacheManager.size());
    }

    @Test
    public void testReadingSystemProperties() {
        ProxyServer.Properties properties = ProxyServer.getSystemProperties();
        assertThat(properties.eventInterval, is(ConfigProxyStatistics.defaultEventInterval));
        assertThat(properties.configSources.length, is(1));
        assertThat(properties.configSources[0], is(ProxyServer.DEFAULT_PROXY_CONFIG_SOURCES));
    }

    static RawConfig createConfigWithNextConfigGeneration(RawConfig config, int errorCode) {
        return new RawConfig(config.getKey(), config.getDefMd5(),
            config.getPayload(), config.getConfigMd5(),
            config.getGeneration() + 1, errorCode, config.getDefContent(), Optional.empty());
    }

}
