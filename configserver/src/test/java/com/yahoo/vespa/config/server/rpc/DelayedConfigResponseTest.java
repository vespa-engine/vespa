// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.config.provision.ApplicationId;

import com.yahoo.jrt.Request;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.DefContent;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequestV3;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import com.yahoo.vespa.config.protocol.Trace;
import com.yahoo.vespa.config.server.GetConfigContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author lulf
 * @since 5.1
 */
public class DelayedConfigResponseTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testDelayedConfigResponses() throws IOException {

        MockRpc rpc = new MockRpc(13337, temporaryFolder.newFolder());
        DelayedConfigResponses responses = new DelayedConfigResponses(rpc, 1, false);
        assertThat(responses.size(), is(0));
        JRTServerConfigRequest req = createRequest("foo", "md5", "myid", "mymd5", 3, 1000000, "bar");
        req.setDelayedResponse(true);
        GetConfigContext context = GetConfigContext.testContext(ApplicationId.defaultId());
        responses.delayResponse(req, context);
        assertThat(responses.size(), is(0));

        req.setDelayedResponse(false);
        responses.delayResponse(req, context);
        responses.delayResponse(createRequest("foolio", "md5", "myid", "mymd5", 3, 100000, "bar"), context);
        assertThat(responses.size(), is(2));
        assertTrue(req.isDelayedResponse());
        List<DelayedConfigResponses.DelayedConfigResponse> it = responses.allDelayedResponses();
        assertTrue(!it.isEmpty());
    }

    @Test
    public void testDelayResponseRemove() throws IOException {
        GetConfigContext context = GetConfigContext.testContext(ApplicationId.defaultId());
        MockRpc rpc = new MockRpc(13337, temporaryFolder.newFolder());
        DelayedConfigResponses responses = new DelayedConfigResponses(rpc, 1, false);
        responses.delayResponse(createRequest("foolio", "md5", "myid", "mymd5", 3, 100000, "bar"), context);
        assertThat(responses.size(), is(1));
        responses.allDelayedResponses().get(0).cancelAndRemove();
        assertThat(responses.size(), is(0));
    }

    @Test
    public void testDelayedConfigResponse() throws IOException {
        MockRpc rpc = new MockRpc(13337, temporaryFolder.newFolder());
        DelayedConfigResponses responses = new DelayedConfigResponses(rpc, 1, false);
        assertThat(responses.size(), is(0));
        assertThat(responses.toString(), is("DelayedConfigResponses. Average Size=0"));
        JRTServerConfigRequest req = createRequest("foo", "md5", "myid", "mymd5", 3, 100, "bar");
        responses.delayResponse(req, GetConfigContext.testContext(ApplicationId.defaultId()));
        rpc.waitUntilSet(5000);
        assertThat(rpc.latestRequest, is(req));
    }

    private JRTServerConfigRequest createRequest(String configName, String defMd5, String configId, String md5, long generation, long timeout, String namespace) {
        Request request = JRTClientConfigRequestV3.
                createWithParams(new ConfigKey<>(configName, configId, namespace, defMd5, null), DefContent.fromList(Collections.emptyList()),
                                 "fromHost", md5, generation, timeout, Trace.createDummy(), CompressionType.UNCOMPRESSED,
                                 Optional.empty()).getRequest();
        return JRTServerConfigRequestV3.createFromRequest(request);
    }

}
