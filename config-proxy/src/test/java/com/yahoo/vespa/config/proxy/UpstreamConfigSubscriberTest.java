// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.impl.JRTConfigRequester;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.*;
import com.yahoo.vespa.config.protocol.*;
import com.yahoo.vespa.config.util.ConfigUtils;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

/**
 * @author hmusum
 */
public class UpstreamConfigSubscriberTest {
    private final ConfigSourceSet sourceSet = new ConfigSourceSet("tcp/foo:78");
    private final TimingValues timingValues = ProxyServer.defaultTimingValues();

    private MapBackedConfigSource sourceResponses;
    private MockClientUpdater clientUpdater;
    private MockConnection mockConnection;
    private long generation = 1;


    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() {
        clientUpdater = MockClientUpdater.create(new MemoryCache());
        sourceResponses = new MapBackedConfigSource(clientUpdater);
        mockConnection = new MockConnection(sourceResponses);
    }

    @Test
    public void basic() {
        RawConfig fooConfig = Helper.fooConfig;
        sourceResponses.put(fooConfig.getKey(), fooConfig);

        UpstreamConfigSubscriber subscriber = createUpstreamConfigSubscriber(fooConfig);
        waitForConfigGeneration(clientUpdater, fooConfig.getKey(), generation);
        assertThat(clientUpdater.getLastConfig(), is(fooConfig));
        subscriber.cancel();
    }

    @Test
    public void require_that_reconfiguration_works() {
        RawConfig fooConfig = Helper.fooConfig;
        sourceResponses.put(fooConfig.getKey(), fooConfig);

        UpstreamConfigSubscriber subscriber = createUpstreamConfigSubscriber(fooConfig);
        waitForConfigGeneration(clientUpdater, fooConfig.getKey(), generation);
        assertThat(clientUpdater.getLastConfig(), is(fooConfig));

        // Update payload in config
        generation++;
        final ConfigPayload payload = Helper.createConfigPayload("bar", "value2");
        RawConfig fooConfig2 = createRawConfig(fooConfig, payload);
        sourceResponses.put(fooConfig2.getKey(), fooConfig2);

        waitForConfigGeneration(clientUpdater, fooConfig2.getKey(), generation);
        assertFalse(clientUpdater.getLastConfig().equals(fooConfig));
        subscriber.cancel();
    }

    @Test
    public void require_that_error_response_is_handled() {
        RawConfig fooConfig = Helper.fooConfig;
        sourceResponses.put(fooConfig.getKey(), fooConfig);

        // Create config with error based on fooConfig
        generation++;
        ConfigPayload errorConfigPayload = new ConfigPayload(new Slime());
        Payload errorPayload = Payload.from(errorConfigPayload);
        ConfigKey<?> errorConfigKey = new ConfigKey<>("error", fooConfig.getConfigId(), fooConfig.getNamespace());
        RawConfig errorConfig = new RawConfig(errorConfigKey, fooConfig.getDefMd5(), errorPayload,
                                              ConfigUtils.getMd5(errorConfigPayload), generation,
                                              ErrorCode.UNKNOWN_DEFINITION, fooConfig.getDefContent(), Optional.empty());

        sourceResponses.put(errorConfigKey, errorConfig);
        UpstreamConfigSubscriber subscriber = createUpstreamConfigSubscriber(errorConfig);
        waitForConfigGeneration(clientUpdater, errorConfigKey, generation);
        RawConfig lastConfig = clientUpdater.getLastConfig();
        assertEquals(errorConfig, lastConfig);
        assertThat(lastConfig.errorCode(), is(ErrorCode.UNKNOWN_DEFINITION));
        subscriber.cancel();
    }

    private Map<ConfigSourceSet, JRTConfigRequester> createRequesterPool() {
        JRTConfigRequester request = JRTConfigRequester.get(mockConnection, timingValues);

        Map<ConfigSourceSet, JRTConfigRequester> requesterPool = new LinkedHashMap<>();
        requesterPool.put(sourceSet, request);
        return requesterPool;
    }

    private void waitForConfigGeneration(MockClientUpdater clientUpdater, ConfigKey<?> configKey, long expectedGeneration) {
        Instant end = Instant.now().plus(Duration.ofSeconds(60));
        RawConfig lastConfig;
        do {
            lastConfig = clientUpdater.getLastConfig();
            System.out.println("config=" + lastConfig + (lastConfig == null ? "" : ",generation=" + lastConfig.getGeneration()));
            if (lastConfig != null && lastConfig.getKey().equals(configKey) &&
                    lastConfig.getGeneration() == expectedGeneration) {
                break;
            } else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        } while (Instant.now().isBefore(end));

        assertNotNull(lastConfig);
        assertThat(lastConfig.getGeneration(), is(expectedGeneration));
        assertThat(lastConfig.getKey(), is(configKey));
    }

    static class MockClientUpdater extends ClientUpdater {
        private RawConfig lastConfig;

        private MockClientUpdater(ConfigProxyStatistics statistics, Mode mode, MemoryCache memoryCache) {
            super(memoryCache, new MockRpcServer(), statistics, new DelayedResponses(statistics), mode);
        }

        static MockClientUpdater create(MemoryCache memoryCache) {
            Mode mode = new Mode();
            ConfigProxyStatistics statistics = new ConfigProxyStatistics();
            return new MockClientUpdater(statistics, mode, memoryCache);
        }

        @Override
        public void updateSubscribers(RawConfig newConfig) {
            lastConfig = newConfig;
        }

        RawConfig getLastConfig() {
            return lastConfig;
        }
    }

    private UpstreamConfigSubscriber createUpstreamConfigSubscriber(RawConfig config) {
        UpstreamConfigSubscriber subscriber = new UpstreamConfigSubscriber(config, clientUpdater, sourceSet, timingValues, createRequesterPool());
        subscriber.subscribe();
        new Thread(subscriber).start();
        return subscriber;
    }

    // Create new config based on another one
    private RawConfig createRawConfig(RawConfig config, ConfigPayload configPayload) {
        final int errorCode = 0;
        Payload fooPayload = Payload.from(configPayload);
        return new RawConfig(config.getKey(), config.getDefMd5(), fooPayload,
                             ConfigUtils.getMd5(configPayload), generation, errorCode,
                             config.getDefContent(), Optional.empty());
    }

}
