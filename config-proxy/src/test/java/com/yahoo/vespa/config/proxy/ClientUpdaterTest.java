// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

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
import static org.junit.Assert.assertThat;

/**
 * @author hmusum
 */
public class ClientUpdaterTest {
    private MockRpcServer rpcServer;
    private ConfigProxyStatistics statistics;
    private DelayedResponses delayedResponses;
    private ClientUpdater clientUpdater;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();


    @Before
    public void setup() {
        rpcServer = new MockRpcServer();
        statistics = new ConfigProxyStatistics();
        delayedResponses = new DelayedResponses(statistics);
        clientUpdater = new ClientUpdater(rpcServer, statistics, delayedResponses);
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
    }

    @Test
    public void errorResponse() {
        assertThat(rpcServer.responses, is(0L));

        clientUpdater.updateSubscribers(ProxyServerTest.errorConfig);
        assertThat(rpcServer.responses, is(0L));
        assertThat(statistics.errors(), is(1L));
    }

    @Test
    public void it_does_not_send_old_config_in_response() {
        assertThat(rpcServer.responses, is(0L));

        RawConfig fooConfigOldGeneration = ProxyServerTest.fooConfig;

        final RawConfig fooConfig = ProxyServerTest.createConfigWithNextConfigGeneration(fooConfigOldGeneration, 0);
        clientUpdater.updateSubscribers(fooConfig);

        // No delayed response, so not returned
        assertEquals(0, rpcServer.responses);

        delayedResponses.add(new DelayedResponse(JRTServerConfigRequestV3.createFromRequest(JRTConfigRequestFactory.createFromRaw(fooConfig, -10L).getRequest())));
        clientUpdater.updateSubscribers(fooConfig);
        assertEquals(1, rpcServer.responses);

        delayedResponses.add(new DelayedResponse(JRTServerConfigRequestV3.createFromRequest(JRTConfigRequestFactory.createFromRaw(fooConfig, -10L).getRequest())));
        clientUpdater.updateSubscribers(fooConfigOldGeneration);
        // Old config generation, so not returned
        assertEquals(1, rpcServer.responses);
    }

}
