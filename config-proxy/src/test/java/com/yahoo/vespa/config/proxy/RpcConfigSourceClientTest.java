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

import static org.junit.Assert.assertEquals;

/**
 * @author hmusum
 */
public class RpcConfigSourceClientTest {

    private MockRpcServer rpcServer;
    private DelayedResponses delayedResponses;
    private RpcConfigSourceClient rpcConfigSourceClient;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();


    @Before
    public void setup() {
        rpcServer = new MockRpcServer();
        delayedResponses = new DelayedResponses();
        rpcConfigSourceClient = new RpcConfigSourceClient(rpcServer, new MockConfigSource(), new MemoryCache(), delayedResponses);
    }

    @Test
    public void basic() {
        final RawConfig fooConfig = ProxyServerTest.fooConfig;
        configUpdatedSendResponse(fooConfig);
        // Nobody asked for the config, so no response sent
        assertSentResponses(0);

        simulateClientRequestingConfig(fooConfig);
        configUpdatedSendResponse(fooConfig);
        assertSentResponses(1);

        // Nobody asked for 'bar' config
        RawConfig barConfig = new RawConfig(new ConfigKey<>("bar", "id", "namespace"), fooConfig.getDefMd5());
        configUpdatedSendResponse(barConfig);
        assertSentResponses(1);
    }

    @Test
    public void errorResponse() {
        configUpdatedSendResponse(ProxyServerTest.errorConfig);
        assertSentResponses(0);
    }

    @Test
    public void it_does_not_send_old_config_in_response() {
        RawConfig fooConfigOldGeneration = ProxyServerTest.fooConfig;

        RawConfig fooConfig = createConfigWithNextConfigGeneration(fooConfigOldGeneration);
        configUpdatedSendResponse(fooConfig);

        // Nobody asked for the config
        assertSentResponses(0);

        simulateClientRequestingConfig(fooConfig);
        configUpdatedSendResponse(fooConfig);
        assertSentResponses(1);

        simulateClientRequestingConfig(fooConfig);
        configUpdatedSendResponse(fooConfigOldGeneration);
        // Old config generation, so no response returned
        assertSentResponses(1);
    }

    @Test
    public void it_does_send_config_with_generation_0_in_response() {
        RawConfig fooConfigOldGeneration = ProxyServerTest.fooConfig;

        RawConfig fooConfig = createConfigWithNextConfigGeneration(fooConfigOldGeneration, 1);

        simulateClientRequestingConfig(fooConfig);
        configUpdatedSendResponse(fooConfig);
        assertSentResponses(1);

        RawConfig fooConfig2 = createConfigWithNextConfigGeneration(fooConfigOldGeneration, 0);
        simulateClientRequestingConfig(fooConfig2);
        configUpdatedSendResponse(fooConfig2);
        assertSentResponses(2);
    }

    private void assertSentResponses(int expected) {
        assertEquals(expected, rpcServer.responses);
    }

    private void simulateClientRequestingConfig(RawConfig config) {
        delayedResponses.add(new DelayedResponse(JRTServerConfigRequestV3.createFromRequest(JRTConfigRequestFactory.createFromRaw(config, -10L).getRequest())));
    }

    private void configUpdatedSendResponse(RawConfig config) {
        rpcConfigSourceClient.updateSubscribers(config);
    }

    private RawConfig createConfigWithNextConfigGeneration(RawConfig config) {
        return createConfigWithNextConfigGeneration(config, config.getGeneration() + 1);
    }

    private RawConfig createConfigWithNextConfigGeneration(RawConfig config, long newConfigGeneration) {
        final int errorCode = 0;
        return ProxyServerTest.createConfigWithNextConfigGeneration(config, errorCode, ProxyServerTest.fooConfig.getPayload(), newConfigGeneration);
    }

}
