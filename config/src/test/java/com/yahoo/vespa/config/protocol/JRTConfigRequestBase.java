// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.config.subscription.ConfigSet;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.config.subscription.impl.GenericConfigSubscriber;
import com.yahoo.config.subscription.impl.JRTConfigRequester;
import com.yahoo.config.subscription.impl.JRTConfigSubscription;
import com.yahoo.config.subscription.impl.MockConnection;
import com.yahoo.jrt.Request;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.Slime;
import com.yahoo.test.ManualClock;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.ErrorCode;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.vespa.config.util.ConfigUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author lulf
 * @since 5.3
 */
public abstract class JRTConfigRequestBase {

    protected String defName = "mydef";
    protected String defNamespace = "my.name.space";
    protected String hostname = "myhost";
    protected String configId = "config/id";
    protected String defMd5 = "595f44fec1e92a71d3e9e77456ba80d1";
    protected long currentGeneration = 3;
    protected final Optional<VespaVersion> vespaVersion = Optional.of(VespaVersion.fromString("5.38.24"));
    protected long timeout = 5000;
    protected Trace trace ;
    protected String configMd5 = ConfigUtils.getMd5(createPayload().getData());
    protected JRTClientConfigRequest clientReq;
    protected JRTServerConfigRequest serverReq;

    @Before
    public void setupRequest() throws IOException {
        clientReq = createReq();
        serverReq = createReq(clientReq.getRequest());
        assertTrue(serverReq.validateParameters());
    }

    private JRTClientConfigRequest createReq() throws IOException {
        trace = Trace.createNew(3, new ManualClock());
        trace.trace(1, "hei");
        return createReq(defName, defNamespace, defMd5, hostname, configId, configMd5, currentGeneration, timeout, trace);
    }

    private JRTClientConfigRequest createReq(Payload payload) throws IOException {
        trace = Trace.createNew(3, new ManualClock());
        trace.trace(1, "hei");
        return createReq(defName, defNamespace, defMd5, hostname, configId, ConfigUtils.getMd5(payload.getData()), currentGeneration, timeout, trace);
    }

    protected abstract JRTClientConfigRequest createReq(String defName, String defNamespace, String defMd5,
                                                        String hostname, String configId, String configMd5,
                                                        long currentGeneration, long timeout, Trace trace);
    protected abstract JRTServerConfigRequest createReq(Request request);
    protected abstract JRTClientConfigRequest createReq(JRTConfigSubscription<SimpletypesConfig> sub, Trace aNew);
    protected abstract JRTClientConfigRequest createFromRaw(RawConfig rawConfig, long serverTimeout, Trace aNew);

    protected void request_is_parsed_base() {
        String [] expectedContent = new String[]{
                "namespace=my.name.space",
                "myfield string"
        };
        System.out.println(serverReq.toString());
        assertThat(serverReq.getConfigKey().getName(), is(defName));
        assertThat(serverReq.getConfigKey().getNamespace(), is(defNamespace));
        assertThat(serverReq.getConfigKey().getMd5(), is(defMd5));
        assertThat(serverReq.getConfigKey().getConfigId(), is(configId));
        assertThat(serverReq.getDefContent().asStringArray(), is(expectedContent));
        assertFalse(serverReq.noCache());
        assertTrue(serverReq.getRequestTrace().toString().contains("hi"));
        assertThat(serverReq.getRequestConfigMd5(), is(configMd5));
        assertThat(serverReq.getRequestGeneration(), is(currentGeneration));
    }

    @Test
    public void delay_mechanisms_functions() {
        assertFalse(serverReq.isDelayedResponse());
        serverReq.setDelayedResponse(true);
        assertTrue(serverReq.isDelayedResponse());
        serverReq.setDelayedResponse(false);
        assertFalse(serverReq.isDelayedResponse());
    }

    public JRTServerConfigRequest next_request_is_correct_base() {
        String [] expectedContent = new String[]{
                "namespace=my.name.space",
                "myfield string"
        };
        JRTServerConfigRequest next = createReq(clientReq.nextRequest(6).getRequest());
        assertThat(next.getConfigKey().getName(), is(defName));
        assertThat(next.getConfigKey().getNamespace(), is(defNamespace));
        assertThat(next.getConfigKey().getMd5(), is(defMd5));
        assertThat(next.getConfigKey().getConfigId(), is(configId));
        assertThat(next.getDefContent().asStringArray(), is(expectedContent));
        assertFalse(next.noCache());
        assertThat(next.getTimeout(), is(6L));
        assertThat(next.getTimeout(), is(6L));
        return next;
    }


    @Test
    public void next_request_when_error_is_correct() {
        serverReq.addOkResponse(createPayload(), 999999, "newmd5");
        serverReq.addErrorResponse(ErrorCode.OUTDATED_CONFIG, "error message");
        System.out.println(serverReq);
        JRTClientConfigRequest next = clientReq.nextRequest(6);
        System.out.println(next);
        // Should use config md5 and generation from the request, not the response
        // when there are errors
        assertThat(next.getRequestConfigMd5(), is(clientReq.getRequestConfigMd5()));
        assertThat(next.getRequestGeneration(), is(clientReq.getRequestGeneration()));
    }

    @Test
    public void ok_response_is_added() {
        Payload payload = createPayload("vale");
        String md5 = ConfigUtils.getMd5(payload.getData());
        long generation = 4L;
        serverReq.addOkResponse(payload, generation, md5);
        assertTrue(clientReq.validateResponse());
        assertThat(clientReq.getNewPayload().withCompression(CompressionType.UNCOMPRESSED).getData().toString(), is(payload.getData().toString()));
        assertThat(clientReq.getNewGeneration(), is(4L));
        assertThat(clientReq.getNewConfigMd5(), is(md5));
        assertTrue(clientReq.hasUpdatedConfig());
        assertTrue(clientReq.hasUpdatedGeneration());
    }

    @Test
    public void error_response_adds_common_elements() {
        serverReq.addErrorResponse(ErrorCode.APPLICATION_NOT_LOADED, ErrorCode.getName(ErrorCode.APPLICATION_NOT_LOADED));
        assertThat(serverReq.getRequest().returnValues().size(), is(1));
        Slime data = new JsonDecoder().decode(new Slime(), Utf8.toBytes(serverReq.getRequest().returnValues().get(0).asString()));
        Inspector response = data.get();
        assertThat(response.field(SlimeResponseData.RESPONSE_DEF_NAME).asString(), is(defName));
        assertThat(response.field(SlimeResponseData.RESPONSE_DEF_NAMESPACE).asString(), is(defNamespace));
        assertThat(response.field(SlimeResponseData.RESPONSE_DEF_MD5).asString(), is(defMd5));
        assertThat(response.field(SlimeResponseData.RESPONSE_CONFIGID).asString(), is(configId));
        assertThat(response.field(SlimeResponseData.RESPONSE_CLIENT_HOSTNAME).asString(), is(hostname));
        Trace t = Trace.fromSlime(response.field(SlimeResponseData.RESPONSE_TRACE));
        assertThat(t.toString(), is(trace.toString()));
    }

    @Test
    public void generation_only_is_updated() {
        Payload payload = createPayload();
        serverReq.addOkResponse(payload, 4L, ConfigUtils.getMd5(payload.getData()));
        boolean value = clientReq.validateResponse();
        assertTrue(clientReq.errorMessage(), value);
        assertFalse(clientReq.hasUpdatedConfig());
        assertTrue(clientReq.hasUpdatedGeneration());
    }

    protected static Payload createPayload() {
        return createPayload("bar");
    }

    private static Payload createPayload(String value) {
        Slime slime = new Slime();
        slime.setObject().setString("myfield", value);
        return Payload.from(new ConfigPayload(slime));
    }

    @Test
    public void nothing_is_updated() {
        Payload payload = createPayload();
        serverReq.addOkResponse(payload, currentGeneration, configMd5);
        assertTrue(clientReq.validateResponse());
        assertFalse(clientReq.hasUpdatedConfig());
        assertFalse(clientReq.hasUpdatedGeneration());
    }

    @Test
    public void payload_is_empty() throws IOException {
        Payload payload = Payload.from(ConfigPayload.empty());
        clientReq = createReq(payload);
        serverReq = createReq(clientReq.getRequest());
        serverReq.addOkResponse(payload, currentGeneration, ConfigUtils.getMd5(payload.getData()));
        boolean val = clientReq.validateResponse();
        assertTrue(clientReq.errorMessage(), val);
        assertFalse(clientReq.hasUpdatedConfig());
        assertFalse(clientReq.hasUpdatedGeneration());
    }

    @Test
    public void request_interface_is_implemented() {
        JRTClientConfigRequest request = clientReq;
        assertFalse(request.containsPayload());
        assertFalse(request.isError());
        assertThat(request.errorCode(), is(clientReq.getRequest().errorCode()));
        assertThat(request.errorMessage(), is(clientReq.getRequest().errorMessage()));
        assertNotNull(request.getRequest());
        assertFalse(request.validateResponse());
        //assertNull(request.getNewPayload().getData());
        assertThat(request.getTimeout(), is(timeout));
        assertFalse(request.hasUpdatedConfig());
        assertFalse(request.hasUpdatedGeneration());
    }

    @Test
    public void created_from_subscription() {
        ConfigSubscriber subscriber = new ConfigSubscriber();
        JRTConfigSubscription<SimpletypesConfig> sub = new JRTConfigSubscription<>(new ConfigKey<>(SimpletypesConfig.class, configId), subscriber, new ConfigSet(), new TimingValues());
        JRTClientConfigRequest request = createReq(sub, Trace.createNew(9));
        assertThat(request.getConfigKey().getName(), is(SimpletypesConfig.CONFIG_DEF_NAME));
        JRTServerConfigRequest serverRequest = createReq(request.getRequest());
        assertTrue(serverRequest.validateParameters());
    }

    @Test
    public void created_from_existing_subscription() {
        System.setProperty("VESPA_CONFIG_PROTOCOL_VERSION", getProtocolVersion());
        MockConnection connection = new MockConnection(new MockConnection.AbstractResponseHandler() {
            @Override
            protected void createResponse() {
                JRTServerConfigRequest serverRequest = createReq(request);
                serverRequest.addOkResponse(createPayload(), currentGeneration, configMd5);
            }
        });

        ConfigSourceSet src = new ConfigSourceSet();
        ConfigSubscriber subscriber = new GenericConfigSubscriber(Collections.singletonMap(src, JRTConfigRequester.get(connection, new TimingValues())));
        JRTConfigSubscription<SimpletypesConfig> sub = new JRTConfigSubscription<>(new ConfigKey<>(SimpletypesConfig.class, configId), subscriber, src, new TimingValues());
        sub.subscribe(120_0000);
        assertTrue(sub.nextConfig(120_0000));
        sub.close();
        JRTClientConfigRequest nextReq = createReq(sub, Trace.createNew());
        SimpletypesConfig config = sub.getConfig();
        assertThat(nextReq.getRequestConfigMd5(), is(config.getConfigMd5()));
        assertThat(nextReq.getRequestGeneration(), is(currentGeneration));
        System.setProperty("VESPA_CONFIG_PROTOCOL_VERSION", "");
    }

    protected abstract String getProtocolVersion();

    @Test
    public void created_from_raw() throws IOException {
        RawConfig rawConfig = new RawConfig(new ConfigKey<>(defName, configId, defNamespace), defMd5);
        long serverTimeout = 100000L;
        JRTClientConfigRequest request = createFromRaw(rawConfig, serverTimeout, Trace.createNew(9));
        assertThat(request.getConfigKey().getName(), is(defName));
        JRTServerConfigRequest serverRequest = createReq(request.getRequest());
        assertTrue(serverRequest.validateParameters());
        assertThat(serverRequest.getTimeout(), is(serverTimeout));
        assertThat(serverRequest.getDefContent().asList(), is(rawConfig.getDefContent()));
    }


    @Test
    public void parameters_are_validated() throws IOException {
        assertTrue(serverReq.validateParameters());
        assertValidationFail(createReq("35#$#!$@#", defNamespace, defMd5, hostname, configId, configMd5, currentGeneration, timeout, trace));
        assertValidationFail(createReq(defName, "abcd.o#$*(!&$", defMd5, hostname, configId, configMd5, currentGeneration, timeout, trace));
        assertValidationFail(createReq(defName, defNamespace, "34", hostname, configId, "34", currentGeneration, timeout, trace));
        assertValidationFail(createReq(defName, defNamespace, defMd5, hostname, configId, "34", currentGeneration, timeout, trace));
        assertValidationFail(createReq(defName, defNamespace, defMd5, hostname, configId, configMd5, -34, timeout, trace));
        assertValidationFail(createReq(defName, defNamespace, defMd5, hostname, configId, configMd5, currentGeneration, -23, trace));
        assertValidationFail(createReq(defName, defNamespace, defMd5, "", configId, configMd5, currentGeneration, timeout, trace));
    }

    private void assertValidationFail(JRTClientConfigRequest req) {
        assertFalse(createReq(req.getRequest()).validateParameters());
    }
}
