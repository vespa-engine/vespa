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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
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
    private static RawConfig fooConfig;
    private static RawConfig errorConfig;
    private static ConfigKey<?> errorConfigKey;
    private static Payload fooPayload;
    private long generation = 1;


    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() {
        clientUpdater = MockClientUpdater.create(new MemoryCache());
        sourceResponses = new MapBackedConfigSource(clientUpdater);

        ConfigPayload payload = getConfigPayload("bar", "value");
        fooPayload = Payload.from(payload);
        fooConfig = new RawConfig(Helper.fooConfig.getKey(), Helper.fooConfig.getDefMd5(), fooPayload, ConfigUtils.getMd5(payload), generation, 0, Helper.fooConfig.getDefContent(), Optional.empty());

        payload = new ConfigPayload(new Slime());
        Payload errorPayload = Payload.from(payload);
        errorConfigKey = new ConfigKey<>("error", fooConfig.getConfigId(), fooConfig.getNamespace());
        errorConfig = new RawConfig(errorConfigKey, fooConfig.getDefMd5(), errorPayload, ConfigUtils.getMd5(payload), generation, ErrorCode.UNKNOWN_DEFINITION, fooConfig.getDefContent(), Optional.empty());

        sourceResponses.clear();
        sourceResponses.put(fooConfig.getKey(), fooConfig);

        mockConnection = new MockConnection(sourceResponses);
    }

    @Test
    public void basic() {
        UpstreamConfigSubscriber subscriber = createUpstreamConfigSubscriber();
        waitForConfigGeneration(clientUpdater, generation);
        assertThat(clientUpdater.getLastConfig(), is(fooConfig));
        subscriber.cancel();
    }

    @Test
    public void require_that_reconfiguration_works() {
        UpstreamConfigSubscriber subscriber = createUpstreamConfigSubscriber();
        waitForConfigGeneration(clientUpdater, generation);
        assertThat(clientUpdater.getLastConfig(), is(fooConfig));

        // Add updated config
        generation++;
        final ConfigPayload payload = getConfigPayload("bar", "value2");
        fooPayload = Payload.from(payload);
        RawConfig fooConfig2 = new RawConfig(fooConfig.getKey(), fooConfig.getDefMd5(), fooPayload, ConfigUtils.getMd5(payload), generation, fooConfig.getDefContent(), Optional.empty());
        sourceResponses.put(fooConfig2.getKey(), fooConfig2);

        waitForConfigGeneration(clientUpdater, generation);
        assertThat(clientUpdater.getLastConfig(), is(not(fooConfig)));
        subscriber.cancel();
    }

    @Test
    public void require_that_error_response_is_handled() {
        sourceResponses.put(errorConfigKey, errorConfig);
        UpstreamConfigSubscriber subscriber = createUpstreamConfigSubscriber();
        waitForConfigGeneration(clientUpdater, generation);
        RawConfig lastConfig = clientUpdater.getLastConfig();
        assertThat(lastConfig, is(errorConfig));
        assertThat(lastConfig.errorCode(), is(ErrorCode.UNKNOWN_DEFINITION));
        subscriber.cancel();
    }

    private UpstreamConfigSubscriber createUpstreamConfigSubscriber(RawConfig config) {
        return new UpstreamConfigSubscriber(config, clientUpdater, sourceSet, timingValues, createRequesterPool());
    }

    private Map<ConfigSourceSet, JRTConfigRequester> createRequesterPool() {
        JRTConfigRequester request = JRTConfigRequester.get(mockConnection, timingValues);

        Map<ConfigSourceSet, JRTConfigRequester> requesterPool = new LinkedHashMap<>();
        requesterPool.put(sourceSet, request);
        return requesterPool;
    }

    private void waitForConfigGeneration(MockClientUpdater clientUpdater, long expectedGeneration) {
        int i = 0;
        RawConfig lastConfig;
        do {
            lastConfig = clientUpdater.getLastConfig();
            if (lastConfig != null) {
                System.out.println("i=" + i + ", config=" + lastConfig + ",generation=" + lastConfig.getGeneration());
            }
            if (lastConfig != null && lastConfig.getGeneration() == expectedGeneration) {
                break;
            } else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    fail(e.getMessage());
                }
            }
            i++;
        } while (i < 1000);
        assertNotNull(lastConfig);
        assertThat(lastConfig.getGeneration(), is(expectedGeneration));
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

    private UpstreamConfigSubscriber createUpstreamConfigSubscriber() {
        UpstreamConfigSubscriber subscriber = createUpstreamConfigSubscriber(fooConfig);
        subscriber.subscribe();
        new Thread(subscriber).start();
        return subscriber;
    }

    private ConfigPayload getConfigPayload(String key, String value) {
        Slime slime = new Slime();
        slime.setObject().setString(key, value);
        return new ConfigPayload(slime);
    }

}
