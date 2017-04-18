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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
        assertEquals(0, rpcServer.responses);

        delayedResponses.add(new DelayedResponse(JRTServerConfigRequestV3.createFromRequest(JRTConfigRequestFactory.createFromRaw(fooConfig, -10L).getRequest())));
        clientUpdater.updateSubscribers(fooConfig);
        assertEquals(1, rpcServer.responses);

        // Will not find bar config in delayed responses
        RawConfig barConfig = new RawConfig(new ConfigKey<>("bar", "id", "namespace"), fooConfig.getDefMd5());
        clientUpdater.updateSubscribers(barConfig);
        assertEquals(1, rpcServer.responses);


        mode = new Mode(Mode.ModeName.MEMORYCACHE);
        // Nothing should be returned, so still 1 response
        assertEquals(1, rpcServer.responses);
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


}
