// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.JRTConfigRequestFactory;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static junit.framework.TestCase.assertNull;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author hmusum
 */
public class ClientUpdaterTest {
    private MockRpcServer rpcServer;
    private ConfigProxyStatistics statistics;
    private DelayedResponses delayedResponses;
    private Mode mode;
    private MemoryCache memoryCache;
    private ClientUpdater clientUpdater;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();


    @Before
    public void setup() {
        rpcServer = new MockRpcServer();
        statistics = new ConfigProxyStatistics();
        delayedResponses = new DelayedResponses(statistics);
        mode = new Mode();
        memoryCache = new MemoryCache();
        clientUpdater = new ClientUpdater(memoryCache, rpcServer, statistics, delayedResponses, mode);
    }

    @Test
    public void basic() {
        assertThat(rpcServer.responses, is(0L));

        final RawConfig fooConfig = ProxyServerTest.fooConfig;
        clientUpdater.updateSubscribers(fooConfig);

        // No delayed response, so not returned
        assertResponseAndCache(rpcServer, memoryCache, fooConfig, 0, 1);

        delayedResponses.add(new DelayedResponse(JRTServerConfigRequestV3.createFromRequest(JRTConfigRequestFactory.createFromRaw(fooConfig, -10L).getRequest())));
        clientUpdater.updateSubscribers(fooConfig);
        assertResponseAndCache(rpcServer, memoryCache, fooConfig, 1, 1);

        // Will not find bar config in delayed responses
        RawConfig barConfig = new RawConfig(new ConfigKey<>("bar", "id", "namespace"), fooConfig.getDefMd5());
        clientUpdater.updateSubscribers(barConfig);
        assertResponseAndCache(rpcServer, memoryCache, barConfig, 1, 2);


        mode = new Mode(Mode.ModeName.MEMORYCACHE);
        // Nothing should be returned, so still 1 response
        assertResponseAndCache(rpcServer, memoryCache, fooConfig, 1, 2);
        assertThat(statistics.errors(), is(0L));
    }

    @Test
    public void memoryCacheMode() {
        final RawConfig fooConfig = ProxyServerTest.fooConfig;
        mode = new Mode(Mode.ModeName.MEMORYCACHE);
        clientUpdater = new ClientUpdater(memoryCache, rpcServer, statistics,delayedResponses, mode);
        memoryCache.clear();
        assertThat(rpcServer.responses, is(0L));

        clientUpdater.updateSubscribers(fooConfig);
        assertNull(memoryCache.get(new ConfigCacheKey(fooConfig.getKey(), fooConfig.getDefMd5())));
        assertThat(memoryCache.size(), is(0));
        assertThat(rpcServer.responses, is(0L));
    }

    @Test
    public void errorResponse() {
        assertThat(rpcServer.responses, is(0L));

        final RawConfig errorConfig = ProxyServerTest.errorConfig;

        clientUpdater.updateSubscribers(errorConfig);
        // Error response, so not put into cache
        assertNull(memoryCache.get(new ConfigCacheKey(errorConfig.getKey(), errorConfig.getDefMd5())));
        assertThat(rpcServer.responses, is(0L));
        assertThat(statistics.errors(), is(1L));
    }

    private static void assertResponseAndCache(MockRpcServer rpcServer,
                                               MemoryCache memoryCache,
                                               RawConfig expectedConfig,
                                               long expectedResponses,
                                               int cacheSize) {
        assertThat(rpcServer.responses, is(expectedResponses));
        assertThat(memoryCache.size(), is(cacheSize));
        assertThat(memoryCache.get(new ConfigCacheKey(expectedConfig.getKey(), expectedConfig.getDefMd5())), is(expectedConfig));
    }
}
