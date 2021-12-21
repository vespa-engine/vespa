// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.PayloadChecksums;
import com.yahoo.jrt.Request;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.DefContent;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import com.yahoo.vespa.config.protocol.Trace;
import com.yahoo.vespa.config.server.GetConfigContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.config.protocol.JRTClientConfigRequestV3.createWithParams;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class DelayedConfigResponseTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testDelayedConfigResponses() throws IOException {

        MockRpcServer rpc = new MockRpcServer(13337, temporaryFolder.newFolder());
        DelayedConfigResponses responses = new DelayedConfigResponses(rpc, 1, false);
        assertEquals(0, responses.size());
        JRTServerConfigRequest req = createRequest("foo", "myid", 3, 1000000, "bar");
        req.setDelayedResponse(true);
        GetConfigContext context = GetConfigContext.testContext(ApplicationId.defaultId());
        responses.delayResponse(req, context);
        assertEquals(0, responses.size());

        req.setDelayedResponse(false);
        responses.delayResponse(req, context);
        responses.delayResponse(createRequest("foolio", "myid", 3, 100000, "bar"), context);
        assertEquals(2, responses.size());
        assertTrue(req.isDelayedResponse());
        List<DelayedConfigResponses.DelayedConfigResponse> it = responses.allDelayedResponses();
        assertFalse(it.isEmpty());
    }

    @Test
    public void testDelayResponseRemove() throws IOException {
        GetConfigContext context = GetConfigContext.testContext(ApplicationId.defaultId());
        MockRpcServer rpc = new MockRpcServer(13337, temporaryFolder.newFolder());
        DelayedConfigResponses responses = new DelayedConfigResponses(rpc, 1, false);
        responses.delayResponse(createRequest("foolio", "myid", 3, 100000, "bar"), context);
        assertEquals(1, responses.size());
        responses.allDelayedResponses().get(0).cancelAndRemove();
        assertEquals(0, responses.size());
    }

    @Test
    public void testDelayedConfigResponse() throws IOException {
        MockRpcServer rpc = new MockRpcServer(13337, temporaryFolder.newFolder());
        DelayedConfigResponses responses = new DelayedConfigResponses(rpc, 1, false);
        assertEquals(0, responses.size());
        assertEquals("DelayedConfigResponses. Average Size=0", responses.toString());
        JRTServerConfigRequest req = createRequest("foo", "myid", 3, 100, "bar");
        responses.delayResponse(req, GetConfigContext.testContext(ApplicationId.defaultId()));
        rpc.waitUntilSet(Duration.ofSeconds(5));
        assertEquals(req, rpc.latestRequest);
    }

    private JRTServerConfigRequest createRequest(String configName, String configId, long generation, long timeout, String namespace) {
        Request request = createWithParams(new ConfigKey<>(configName, configId, namespace, null),
                                           DefContent.fromList(Collections.emptyList()), "fromHost",
                                           PayloadChecksums.empty(), generation, timeout, Trace.createDummy(),
                                           CompressionType.UNCOMPRESSED, Optional.empty())
                .getRequest();
        return JRTServerConfigRequestV3.createFromRequest(request);
    }

}
