// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.google.common.base.Joiner;
import com.yahoo.cloud.config.LbServicesConfig;
import com.yahoo.config.SimpletypesConfig;
import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.jrt.Request;
import com.yahoo.vespa.config.*;
import com.yahoo.vespa.config.protocol.*;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.util.ConfigUtils;

import com.yahoo.vespa.model.VespaModel;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * @author lulf
 * @since 5.1
 */
public class RpcServerTest extends TestWithRpc {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testRpcServer() throws IOException, SAXException, InterruptedException {
        testPrintStatistics();
        testGetConfig();
        testEnabled();
        testEmptyConfigHostedVespa();
    }

    private void testEmptyConfigHostedVespa() throws InterruptedException, IOException {
        rpcServer.onTenantDelete(TenantName.defaultName());
        rpcServer.onTenantsLoaded();
        JRTClientConfigRequest clientReq = createSimpleRequest();
        performRequest(clientReq.getRequest());
        assertFalse(clientReq.validateResponse());
        assertThat(clientReq.errorCode(), is(ErrorCode.APPLICATION_NOT_LOADED));
        stopRpc();
        createAndStartRpcServer(true);
        rpcServer.onTenantsLoaded();
        clientReq = createSimpleRequest();
        performRequest(clientReq.getRequest());
        assertTrue(clientReq.validateResponse());
    }

    private JRTClientConfigRequest createSimpleRequest() {
        ConfigKey<?> key = new ConfigKey<>(SimpletypesConfig.class, "");
        JRTClientConfigRequest clientReq = JRTClientConfigRequestV3.createFromRaw(new RawConfig(key, SimpletypesConfig.CONFIG_DEF_MD5), 120_000, Trace.createDummy(), CompressionType.UNCOMPRESSED, Optional.empty());
        assertTrue(clientReq.validateParameters());
        return clientReq;
    }


    private void testEnabled() throws IOException, SAXException {
        generationCounter.increment();
        Application app = new Application(new VespaModel(MockApplicationPackage.createEmpty()),
                                          new ServerCache(),
                                          2L,
                                          false,
                                          Version.fromIntValues(1, 2, 3),
                                          MetricUpdater.createTestUpdater(),
                                          ApplicationId.defaultId());
        ApplicationSet appSet = ApplicationSet.fromSingle(app);
        rpcServer.configActivated(TenantName.defaultName(), appSet);
        ConfigKey<?> key = new ConfigKey<>(LbServicesConfig.class, "*");
        JRTClientConfigRequest clientReq = JRTClientConfigRequestV3.createFromRaw(new RawConfig(key, LbServicesConfig.CONFIG_DEF_MD5), 120_000, Trace.createDummy(), CompressionType.UNCOMPRESSED, Optional.empty());
        assertTrue(clientReq.validateParameters());
        performRequest(clientReq.getRequest());
        assertFalse(clientReq.validateResponse());
        assertThat(clientReq.errorCode(), is(ErrorCode.APPLICATION_NOT_LOADED));

        rpcServer.onTenantsLoaded();
        clientReq = JRTClientConfigRequestV3.createFromRaw(new RawConfig(key, LbServicesConfig.CONFIG_DEF_MD5), 120_000, Trace.createDummy(), CompressionType.UNCOMPRESSED, Optional.empty());
        assertTrue(clientReq.validateParameters());
        performRequest(clientReq.getRequest());
        boolean validResponse = clientReq.validateResponse();
        assertTrue(clientReq.errorMessage(), validResponse);
        assertThat(clientReq.errorCode(), is(0));
    }

    public void testGetConfig() {
        ((MockRequestHandler)tenantProvider.getRequestHandler()).throwException = false;
        ConfigKey<?> key = new ConfigKey<>(SimpletypesConfig.class, "brim");
        ((MockRequestHandler)tenantProvider.getRequestHandler()).responses.put(ApplicationId.defaultId(), createResponse());
        JRTClientConfigRequest req = JRTClientConfigRequestV3.createFromRaw(new RawConfig(key, SimpletypesConfig.CONFIG_DEF_MD5), 120_000, Trace.createDummy(), CompressionType.UNCOMPRESSED, Optional.empty());
        assertTrue(req.validateParameters());
        performRequest(req.getRequest());
        assertThat(req.errorCode(), is(0));
        assertTrue(req.validateResponse());
        ConfigPayload payload = ConfigPayload.fromUtf8Array(req.getNewPayload().getData());
        assertNotNull(payload);
        SimpletypesConfig.Builder builder = new SimpletypesConfig.Builder();
        new ConfigPayloadApplier<>(builder).applyPayload(payload);
        SimpletypesConfig config = new SimpletypesConfig(builder);
        assertThat(config.intval(), is(123));
    }

    public ConfigResponse createResponse() {
        SimpletypesConfig.Builder builder = new SimpletypesConfig.Builder();
        builder.intval(123);
        SimpletypesConfig responseConfig = new SimpletypesConfig(builder);
        ConfigPayload responsePayload = ConfigPayload.fromInstance(responseConfig);
        InnerCNode targetDef = new DefParser(SimpletypesConfig.CONFIG_DEF_NAME, new StringReader(Joiner.on("\n").join(SimpletypesConfig.CONFIG_DEF_SCHEMA))).getTree();
        return SlimeConfigResponse.fromConfigPayload(responsePayload, targetDef, 3l, ConfigUtils.getMd5(responsePayload));
    }

    public void testPrintStatistics() {
        Request req = new Request("printStatistics");
        rpcServer.printStatistics(req);
        assertThat(req.returnValues().get(0).asString(), is("Delayed responses queue size: 0"));
    }

}
