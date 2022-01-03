// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.impl.JRTConfigRequester;
import com.yahoo.config.subscription.impl.JRTConfigSubscription;
import com.yahoo.config.subscription.impl.MockConnection;
import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.jrt.Request;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.ErrorCode;
import com.yahoo.vespa.config.JRTConnectionPool;
import com.yahoo.vespa.config.PayloadChecksums;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.vespa.config.util.ConfigUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.config.PayloadChecksum.Type.MD5;
import static com.yahoo.vespa.config.PayloadChecksum.Type.XXHASH64;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class JRTConfigRequestV3Test {

    private static final String [] configDefinition = new String[]{
            "namespace=my.name.space",
            "myfield string"
    };

    private final Optional<VespaVersion> vespaVersion = Optional.of(VespaVersion.fromString("5.38.24"));
    private final String defName = "mydef";
    private final String defNamespace = "my.name.space";
    private final String hostname = "myhost";
    private final String configId = "config/id";
    private final String defMd5 = ConfigUtils.getDefMd5(List.of(configDefinition));
    private final long currentGeneration = 3;
    private final long timeout = 5000;
    private Trace trace ;
    private final PayloadChecksums payloadChecksums = PayloadChecksums.fromPayload(createPayload());
    private JRTClientConfigRequest clientReq;
    private JRTServerConfigRequest serverReq;

    @Before
    public void setupRequest() {
        clientReq = createReq();
        serverReq = createReq(clientReq.getRequest());
        assertTrue(serverReq.validateParameters());
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
        PayloadChecksums payloadChecksums = PayloadChecksums.fromPayload(Payload.from(payload));
        SlimeConfigResponse response = SlimeConfigResponse.fromConfigPayload(payload,
                                                                             0,
                                                                             false,
                                                                             payloadChecksums);
        serverReq.addOkResponse(serverReq.payloadFromResponse(response), response.getGeneration(),  false, payloadChecksums);
        assertTrue(clientReq.validateResponse());
        assertTrue(clientReq.hasUpdatedGeneration());
        assertEquals("{}", clientReq.getNewPayload().withCompression(CompressionType.UNCOMPRESSED).getData().toString());
    }

    @Test
    public void delay_mechanisms_function() {
        assertFalse(serverReq.isDelayedResponse());
        serverReq.setDelayedResponse(true);
        assertTrue(serverReq.isDelayedResponse());
        serverReq.setDelayedResponse(false);
        assertFalse(serverReq.isDelayedResponse());
    }

    @Test
    public void next_request_when_error_is_correct() {
        Payload payload = createPayload();
        serverReq.addOkResponse(payload, 999999, false, PayloadChecksums.fromPayload(payload));
        serverReq.addErrorResponse(ErrorCode.OUTDATED_CONFIG, "error message");
        JRTClientConfigRequest next = clientReq.nextRequest(6);
        // Should use config checksums and generation from the request (not the response) when there are errors
        assertThat(next.getRequestConfigChecksums().getForType(MD5), is(clientReq.getRequestConfigChecksums().getForType(MD5)));
        assertThat(next.getRequestConfigChecksums().getForType(XXHASH64), is(clientReq.getRequestConfigChecksums().getForType(XXHASH64)));
        assertThat(next.getRequestGeneration(), is(clientReq.getRequestGeneration()));
    }

    @Test
    public void ok_response_is_added() {
        Payload payload = createPayload("vale");
        String md5 = ConfigUtils.getMd5(payload.getData());
        String xxhash64 = ConfigUtils.getXxhash64(payload.getData());
        long generation = 4L;
        serverReq.addOkResponse(payload, generation, false, PayloadChecksums.fromPayload(payload));
        assertTrue(clientReq.validateResponse());
        assertThat(clientReq.getNewPayload().withCompression(CompressionType.UNCOMPRESSED).getData().toString(), is(payload.getData().toString()));
        assertThat(clientReq.getNewGeneration(), is(4L));
        assertThat(clientReq.getNewChecksums().getForType(MD5).asString(), is(md5));
        assertThat(clientReq.getNewChecksums().getForType(XXHASH64).asString(), is(xxhash64));
        assertTrue(clientReq.hasUpdatedConfig());
        assertTrue(clientReq.hasUpdatedGeneration());
    }

    @Test
    public void error_response_adds_common_elements() {
        serverReq.addErrorResponse(ErrorCode.APPLICATION_NOT_LOADED, ErrorCode.getName(ErrorCode.APPLICATION_NOT_LOADED));
        assertThat(serverReq.getRequest().returnValues().size(), is(1));
        Inspector response = SlimeUtils.jsonToSlime(serverReq.getRequest().returnValues().get(0).asString()).get();
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
        serverReq.addOkResponse(payload, 4L, false, PayloadChecksums.fromPayload(payload));
        boolean value = clientReq.validateResponse();
        assertTrue(clientReq.errorMessage(), value);
        assertFalse(clientReq.hasUpdatedConfig());
        assertTrue(clientReq.hasUpdatedGeneration());
    }

    @Test
    public void nothing_is_updated() {
        Payload payload = createPayload();
        serverReq.addOkResponse(payload, currentGeneration, false, payloadChecksums);
        assertTrue(clientReq.validateResponse());
        assertFalse(clientReq.hasUpdatedConfig());
        assertFalse(clientReq.hasUpdatedGeneration());
    }

    @Test
    public void payload_is_empty() {
        Payload payload = Payload.from(ConfigPayload.empty());
        clientReq = createReq(payload);
        serverReq = createReq(clientReq.getRequest());
        serverReq.addOkResponse(payload, currentGeneration, false, PayloadChecksums.fromPayload(payload));
        boolean val = clientReq.validateResponse();
        assertTrue(clientReq.errorMessage(), val);
        assertFalse(clientReq.hasUpdatedConfig());
        assertFalse(clientReq.hasUpdatedGeneration());
    }

    @Test
    public void request_interface_is_implemented() {
        JRTClientConfigRequest request = clientReq;
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
        TimingValues timingValues = new TimingValues();
        JRTConfigSubscription<SimpletypesConfig> sub =
                new JRTConfigSubscription<>(new ConfigKey<>(SimpletypesConfig.class, configId),
                                            new JRTConfigRequester(new JRTConnectionPool(new ConfigSourceSet("tcp/localhost:985")), timingValues),
                                            timingValues);
        JRTClientConfigRequest request = createReq(sub, Trace.createNew(9));
        assertThat(request.getConfigKey().getName(), is(SimpletypesConfig.CONFIG_DEF_NAME));
        JRTServerConfigRequest serverRequest = createReq(request.getRequest());
        assertTrue(serverRequest.validateParameters());
    }

    @Test
    public void created_from_existing_subscription() {
        MockConnection connection = new MockConnection(new MockConnection.AbstractResponseHandler() {
            @Override
            public void createResponse(Request request) {
                JRTServerConfigRequest serverRequest = createReq(request);
                serverRequest.addOkResponse(createPayload(), currentGeneration, false, payloadChecksums);
            }
        });

        TimingValues timingValues = new TimingValues();
        JRTConfigSubscription<SimpletypesConfig> sub = new JRTConfigSubscription<>(new ConfigKey<>(SimpletypesConfig.class, configId),
                                                                                   new JRTConfigRequester(connection, timingValues),
                                                                                   timingValues);
        sub.subscribe(120_0000);
        assertTrue(sub.nextConfig(120_0000));
        sub.close();
        JRTClientConfigRequest nextReq = createReq(sub, Trace.createNew());
        assertEquals(nextReq.getRequestConfigChecksums().getForType(MD5).asString(), sub.getConfigState().getChecksums().getForType(MD5).asString());
        assertEquals(nextReq.getRequestConfigChecksums().getForType(XXHASH64).asString(), sub.getConfigState().getChecksums().getForType(XXHASH64).asString());
        assertEquals(nextReq.getRequestGeneration(), currentGeneration);
    }

    @Test
    public void created_from_raw() {
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
    public void parameters_are_validated() {
        assertTrue(serverReq.validateParameters());
        assertValidationFail(createReq("35#$#!$@#", defNamespace, hostname, configId, payloadChecksums, currentGeneration, timeout, trace));
        assertValidationFail(createReq(defName, "abcd.o#$*(!&$", hostname, configId, payloadChecksums, currentGeneration, timeout, trace));
        assertValidationFail(createReq(defName, defNamespace, hostname, configId, PayloadChecksums.from("1234", "opnq"), currentGeneration, timeout, trace));
        assertValidationFail(createReq(defName, defNamespace, hostname, configId, payloadChecksums, -34, timeout, trace));
        assertValidationFail(createReq(defName, defNamespace, hostname, configId, payloadChecksums, currentGeneration, -23, trace));
        assertValidationFail(createReq(defName, defNamespace, "", configId, payloadChecksums, currentGeneration, timeout, trace));
    }

    private void assertValidationFail(JRTClientConfigRequest req) {
        assertFalse(createReq(req.getRequest()).validateParameters());
    }

    private static Payload createPayload() {
        return createPayload("bar");
    }

    private static Payload createPayload(String value) {
        Slime slime = new Slime();
        slime.setObject().setString("myfield", value);
        return Payload.from(new ConfigPayload(slime));
    }

    private JRTClientConfigRequest createReq(String defName, String defNamespace,
                                             String hostname, String configId, PayloadChecksums payloadChecksums,
                                             long currentGeneration, long timeout, Trace trace) {
        return JRTClientConfigRequestV3.createWithParams(ConfigKey.createFull(defName, configId, defNamespace),
                                                         DefContent.fromList(List.of(configDefinition)),
                                                         hostname,
                                                         payloadChecksums,
                                                         currentGeneration,
                                                         timeout,
                                                         trace,
                                                         CompressionType.LZ4,
                                                         vespaVersion);
    }

    private JRTServerConfigRequest createReq(Request request) {
        return JRTServerConfigRequestV3.createFromRequest(request);
    }

    private JRTClientConfigRequest createReq(JRTConfigSubscription<SimpletypesConfig> sub, Trace aNew) {
        return JRTClientConfigRequestV3.createFromSub(sub, aNew, CompressionType.LZ4, vespaVersion);
    }

    private JRTClientConfigRequest createFromRaw(RawConfig rawConfig, long serverTimeout, Trace aNew) {
        return JRTClientConfigRequestV3.createFromRaw(rawConfig, serverTimeout, aNew, CompressionType.LZ4, vespaVersion);
    }

    private JRTClientConfigRequest createReq() {
        trace = Trace.createNew(3, new ManualClock());
        trace.trace(1, "hei");
        return createReq(defName, defNamespace, hostname, configId, payloadChecksums, currentGeneration, timeout, trace);
    }

    private JRTClientConfigRequest createReq(Payload payload) {
        trace = Trace.createNew(3, new ManualClock());
        trace.trace(1, "hei");
        return createReq(defName, defNamespace, hostname, configId, PayloadChecksums.fromPayload(payload), currentGeneration, timeout, trace);
    }

    private void request_is_parsed_base() {
        System.out.println(serverReq.toString());
        assertThat(serverReq.getConfigKey().getName(), is(defName));
        assertThat(serverReq.getConfigKey().getNamespace(), is(defNamespace));
        assertThat(serverReq.getRequestDefMd5(), is(defMd5));
        assertThat(serverReq.getConfigKey().getConfigId(), is(configId));
        assertThat(serverReq.getDefContent().asStringArray(), is(configDefinition));
        assertFalse(serverReq.noCache());
        assertTrue(serverReq.getRequestTrace().toString().contains("hi"));
        assertThat(serverReq.getRequestConfigChecksums().getForType(MD5), is(payloadChecksums.getForType(MD5)));
        assertThat(serverReq.getRequestConfigChecksums().getForType(XXHASH64), is(payloadChecksums.getForType(XXHASH64)));
        assertThat(serverReq.getRequestGeneration(), is(currentGeneration));
    }

    private JRTServerConfigRequest next_request_is_correct_base() {
        JRTServerConfigRequest next = createReq(clientReq.nextRequest(6).getRequest());
        assertThat(next.getConfigKey().getName(), is(defName));
        assertThat(next.getConfigKey().getNamespace(), is(defNamespace));
        assertThat(next.getRequestDefMd5(), is(defMd5));
        assertThat(next.getConfigKey().getConfigId(), is(configId));
        assertThat(next.getDefContent().asStringArray(), is(configDefinition));
        assertFalse(next.noCache());
        assertThat(next.getTimeout(), is(6L));
        assertThat(next.getTimeout(), is(6L));
        return next;
    }

}
