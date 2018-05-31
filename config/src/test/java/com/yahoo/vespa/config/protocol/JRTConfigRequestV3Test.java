// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.config.subscription.impl.JRTConfigSubscription;
import com.yahoo.jrt.Request;
import com.yahoo.vespa.config.*;
import com.yahoo.vespa.config.util.ConfigUtils;
import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author lulf
 * @since 5.19
 */
public class JRTConfigRequestV3Test extends JRTConfigRequestBase {

    @Override
    protected JRTClientConfigRequest createReq(String defName, String defNamespace, String defMd5,
                                               String hostname, String configId, String configMd5,
                                               long currentGeneration, long timeout, Trace trace) {
        return JRTClientConfigRequestV3.createWithParams(ConfigKey.createFull(defName, configId, defNamespace, defMd5),
                DefContent.fromList(Arrays.asList("namespace=my.name.space", "myfield string")),
                hostname,
                configMd5,
                currentGeneration,
                timeout,
                trace,
                CompressionType.LZ4,
                vespaVersion);
    }

    @Override
    protected JRTServerConfigRequest createReq(Request request) {
        return JRTServerConfigRequestV3.createFromRequest(request);
    }

    @Override
    protected JRTClientConfigRequest createReq(JRTConfigSubscription<SimpletypesConfig> sub, Trace aNew) {
        return JRTClientConfigRequestV3.createFromSub(sub, aNew, CompressionType.LZ4, vespaVersion);
    }

    @Override
    protected JRTClientConfigRequest createFromRaw(RawConfig rawConfig, long serverTimeout, Trace aNew) {
        return JRTClientConfigRequestV3.createFromRaw(rawConfig, serverTimeout, aNew, CompressionType.LZ4, vespaVersion);
    }

    @Override
    protected String getProtocolVersion() {
        return "3";
    }

    @Test
    public void request_is_parsed() {
        request_is_parsed_base();
        assertThat(serverReq.getVespaVersion().toString(), is(vespaVersion.toString()));
    }

    @Test
    public void next_request_is_correct() {
        JRTServerConfigRequest next = next_request_is_correct_base();
        assertThat(next.getVespaVersion().toString(), is(vespaVersion.toString()));
    }

    @Test
    public void emptypayload() {
        ConfigPayload payload = ConfigPayload.empty();
        SlimeConfigResponse response = SlimeConfigResponse.fromConfigPayload(payload, null, 0, ConfigUtils.getMd5(payload));
        serverReq.addOkResponse(serverReq.payloadFromResponse(response), response.getGeneration(), false, response.getConfigMd5());
        assertTrue(clientReq.validateResponse());
        assertTrue(clientReq.hasUpdatedGeneration());
        assertThat(clientReq.getNewPayload().withCompression(CompressionType.UNCOMPRESSED).getData().toString(), is("{}"));
    }
}
