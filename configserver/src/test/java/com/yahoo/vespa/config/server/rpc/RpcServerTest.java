// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.LbServicesConfig;
import com.yahoo.cloud.config.SentinelConfig;
import com.yahoo.component.Version;
import com.yahoo.config.SimpletypesConfig;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.jrt.Request;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.ConfigPayloadApplier;
import com.yahoo.vespa.config.ErrorCode;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequest;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequestV3;
import com.yahoo.vespa.config.protocol.SlimeConfigResponse;
import com.yahoo.vespa.config.protocol.Trace;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.util.ConfigUtils;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class RpcServerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testRpcServer() throws IOException, SAXException, InterruptedException {
        try (RpcTester tester = new RpcTester(temporaryFolder)) {
            testPrintStatistics(tester);
            testGetConfig(tester);
            testEnabled(tester);
            testApplicationNotLoadedErrorWhenAppDeleted(tester);
            testEmptySentinelConfigWhenAppDeletedOnHostedVespa();
        }
    }

    private void testApplicationNotLoadedErrorWhenAppDeleted(RpcTester tester) throws InterruptedException, IOException {
        tester.rpcServer().onTenantDelete(TenantName.defaultName());
        tester.rpcServer().onTenantsLoaded();
        JRTClientConfigRequest clientReq = createSimpleRequest();
        tester.performRequest(clientReq.getRequest());
        assertFalse(clientReq.validateResponse());
        assertThat(clientReq.errorCode(), is(ErrorCode.APPLICATION_NOT_LOADED));
        tester.stopRpc();
        tester.createAndStartRpcServer();
        tester.rpcServer().onTenantsLoaded();
        clientReq = createSimpleRequest();
        tester.performRequest(clientReq.getRequest());
        assertTrue(clientReq.validateResponse());
    }

    @Test
    public void testEmptySentinelConfigWhenAppDeletedOnHostedVespa() throws IOException, InterruptedException {
        ConfigserverConfig.Builder configBuilder = new ConfigserverConfig.Builder().canReturnEmptySentinelConfig(true);
        try (RpcTester tester = new RpcTester(temporaryFolder, configBuilder)) {
            tester.rpcServer().onTenantDelete(TenantName.defaultName());
            tester.rpcServer().onTenantsLoaded();
            JRTClientConfigRequest clientReq = createSentinelRequest();

            // Should get empty sentinel config when on hosted vespa
            tester.performRequest(clientReq.getRequest());
            assertTrue(clientReq.validateResponse());
            assertEquals(0, clientReq.errorCode());

            ConfigPayload payload = ConfigPayload.fromUtf8Array(clientReq.getNewPayload().getData());
            assertNotNull(payload);
            SentinelConfig.Builder builder = new SentinelConfig.Builder();
            new ConfigPayloadApplier<>(builder).applyPayload(payload);
            SentinelConfig config = new SentinelConfig(builder);
            assertEquals(0, config.service().size());
        }
    }

    private JRTClientConfigRequest createSimpleRequest() {
        ConfigKey<?> key = new ConfigKey<>(SimpletypesConfig.class, "");
        JRTClientConfigRequest clientReq = createRequest(new RawConfig(key, SimpletypesConfig.getDefMd5()));
        assertTrue(clientReq.validateParameters());
        return clientReq;
    }

    private JRTClientConfigRequest createSentinelRequest() {
        ConfigKey<?> key = new ConfigKey<>(SentinelConfig.class, "");
        JRTClientConfigRequest clientReq = createRequest(new RawConfig(key, SentinelConfig.getDefMd5()));
        assertTrue(clientReq.validateParameters());
        return clientReq;
    }

    private void testEnabled(RpcTester tester) throws IOException, SAXException {
        Application app = new Application(new VespaModel(MockApplicationPackage.createEmpty()),
                                          new ServerCache(),
                                          2L,
                                          false,
                                          new Version(1, 2, 3),
                                          MetricUpdater.createTestUpdater(),
                                          ApplicationId.defaultId());
        ApplicationSet appSet = ApplicationSet.fromSingle(app);
        tester.rpcServer().configActivated(appSet);
        ConfigKey<?> key = new ConfigKey<>(LbServicesConfig.class, "*");
        JRTClientConfigRequest clientReq  = createRequest(new RawConfig(key, LbServicesConfig.getDefMd5()));
        assertTrue(clientReq.validateParameters());
        tester.performRequest(clientReq.getRequest());
        assertFalse(clientReq.validateResponse());
        assertThat(clientReq.errorCode(), is(ErrorCode.APPLICATION_NOT_LOADED));

        tester.rpcServer().onTenantsLoaded();
        clientReq = createRequest(new RawConfig(key, LbServicesConfig.getDefMd5()));
        assertTrue(clientReq.validateParameters());
        tester.performRequest(clientReq.getRequest());
        boolean validResponse = clientReq.validateResponse();
        assertTrue(clientReq.errorMessage(), validResponse);
        assertThat(clientReq.errorCode(), is(0));
    }

    private void testGetConfig(RpcTester tester) {
        ConfigKey<?> key = new ConfigKey<>(SimpletypesConfig.class, "brim");
        JRTClientConfigRequest req = createRequest(new RawConfig(key, SimpletypesConfig.getDefMd5()));
        ((MockRequestHandler)tester.tenantProvider().getRequestHandler()).responses.put(ApplicationId.defaultId(), createResponse());
        assertTrue(req.validateParameters());
        tester.performRequest(req.getRequest());
        assertThat(req.errorCode(), is(0));
        assertTrue(req.validateResponse());
        assertTrue(req.responseIsInternalRedeploy());
        ConfigPayload payload = ConfigPayload.fromUtf8Array(req.getNewPayload().getData());
        assertNotNull(payload);
        SimpletypesConfig.Builder builder = new SimpletypesConfig.Builder();
        new ConfigPayloadApplier<>(builder).applyPayload(payload);
        SimpletypesConfig config = new SimpletypesConfig(builder);
        assertThat(config.intval(), is(123));
    }

    private ConfigResponse createResponse() {
        SimpletypesConfig.Builder builder = new SimpletypesConfig.Builder();
        builder.intval(123);
        SimpletypesConfig responseConfig = new SimpletypesConfig(builder);
        ConfigPayload responsePayload = ConfigPayload.fromInstance(responseConfig);
        return SlimeConfigResponse.fromConfigPayload(responsePayload,
                                                     3L,
                                                     true, /* internalRedeploy */
                                                     ConfigUtils.getMd5(responsePayload));
    }

    private void testPrintStatistics(RpcTester tester) {
        Request req = new Request("printStatistics");
        tester.performRequest(req);
        assertThat(req.returnValues().get(0).asString(), is("Delayed responses queue size: 0"));
    }

    private JRTClientConfigRequest createRequest(RawConfig config) {
        return JRTClientConfigRequestV3.createFromRaw(config, 120_000, Trace.createDummy(), CompressionType.UNCOMPRESSED, Optional.empty());
    }

}
