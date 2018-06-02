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
import static org.junit.Assert.*;

/**
 * @author hmusum
 */
public class ProxyServerTest {

    private final MemoryCache memoryCache = new MemoryCache();
    private MockClientUpdater clientUpdater = new MockClientUpdater();
    private final MockConfigSource source = new MockConfigSource(clientUpdater);
    private MockConfigSourceClient client = new MockConfigSourceClient(source, memoryCache);
    private final ConfigProxyStatistics statistics = new ConfigProxyStatistics();
    private ProxyServer proxy;

    static final RawConfig fooConfig = ConfigTester.fooConfig;

    // errorConfig based on fooConfig
    private static final ConfigKey<?> errorConfigKey = new ConfigKey<>("error", fooConfig.getConfigId(), fooConfig.getNamespace());
    static final RawConfig errorConfig = new RawConfig(errorConfigKey, fooConfig.getDefMd5(),
            fooConfig.getPayload(), fooConfig.getConfigMd5(),
            fooConfig.getGeneration(), false, ErrorCode.UNKNOWN_DEFINITION, fooConfig.getDefContent(), Optional.empty());

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() {
        source.clear();
        source.put(fooConfig.getKey(), createConfigWithNextConfigGeneration(fooConfig, 0));
        source.put(errorConfigKey, createConfigWithNextConfigGeneration(fooConfig, ErrorCode.UNKNOWN_DEFINITION));
        proxy = ProxyServer.createTestServer(source, client, memoryCache, statistics);
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

        ConfigTester tester = new ConfigTester();
        final MemoryCache memoryCache = proxy.getMemoryCache();
        assertEquals(0, memoryCache.size());
        RawConfig res = proxy.resolveConfig(tester.createRequest(fooConfig));
        assertNotNull(res);
        assertThat(res.getPayload().toString(), is(ConfigTester.fooPayload.toString()));
        assertEquals(1, memoryCache.size());
        assertThat(memoryCache.get(new ConfigCacheKey(fooConfig.getKey(), fooConfig.getDefMd5())), is(res));


        assertEquals(1, statistics.processedRequests());
        assertEquals(0, statistics.rpcRequests());
        assertEquals(0, statistics.errors());
        assertEquals(0, statistics.delayedResponses());

        statistics.incProcessedRequests();
        statistics.incRpcRequests();
        statistics.incErrorCount();
        statistics.delayedResponses(1);

        assertEquals(2, statistics.processedRequests());
        assertEquals(1, statistics.rpcRequests());
        assertEquals(1, statistics.errors());
        assertEquals(1, statistics.delayedResponses());

        statistics.decDelayedResponses();
        assertEquals(0, statistics.delayedResponses());

        assertEquals(ConfigProxyStatistics.defaultEventInterval, statistics.getEventInterval().longValue());
    }

    /**
     * Tests that the proxy server RPC commands for setting and getting mode works..
     */
    @Test
    public void testModeSwitch() {
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
        assertThat(res.getPayload().toString(), is(ConfigTester.fooPayload.toString()));
        assertEquals(1, cacheManager.size());

        JRTServerConfigRequest newRequestBasedOnResponse = tester.createRequest(res);
        RawConfig res2 = proxy.resolveConfig(newRequestBasedOnResponse);
        assertFalse(ProxyServer.configOrGenerationHasChanged(res2, newRequestBasedOnResponse));
        assertEquals(1, cacheManager.size());
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
    public void testReconfigurationAsClient() {
        long generation = 1;
        RawConfig fooConfig = ConfigTester.fooConfig;
        source.put(fooConfig.getKey(), fooConfig);

        clientUpdater.waitForConfigGeneration(fooConfig.getKey(), generation);
        assertThat(clientUpdater.getLastConfig(), is(fooConfig));

        // Update payload in config
        generation++;
        final ConfigPayload payload = ConfigTester.createConfigPayload("bar", "value2");
        RawConfig fooConfig2 = createConfigWithNextConfigGeneration(fooConfig, 0, Payload.from(payload));
        source.put(fooConfig2.getKey(), fooConfig2);

        clientUpdater.waitForConfigGeneration(fooConfig2.getKey(), generation);
        assertFalse(clientUpdater.getLastConfig().equals(fooConfig));
    }

    @Test
    public void testReadingSystemProperties() {
        ProxyServer.Properties properties = ProxyServer.getSystemProperties();
        assertThat(properties.eventInterval, is(ConfigProxyStatistics.defaultEventInterval));
        assertThat(properties.configSources.length, is(1));
        assertThat(properties.configSources[0], is(ProxyServer.DEFAULT_PROXY_CONFIG_SOURCES));
    }

    static RawConfig createConfigWithNextConfigGeneration(RawConfig config, int errorCode) {
        return createConfigWithNextConfigGeneration(config, errorCode, config.getPayload());
    }

    private static RawConfig createConfigWithNextConfigGeneration(RawConfig config, int errorCode, Payload payload) {
        return new RawConfig(config.getKey(), config.getDefMd5(),
                             payload, config.getConfigMd5(),
                             config.getGeneration() + 1, false,
                             errorCode, config.getDefContent(), Optional.empty());
    }

}
