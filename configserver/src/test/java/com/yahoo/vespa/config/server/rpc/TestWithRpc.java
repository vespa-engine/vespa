// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.google.common.io.Files;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.provision.HostLivenessTracker;
import com.yahoo.config.provision.TenantName;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.net.HostName;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.config.GenerationCounter;
import com.yahoo.vespa.config.server.*;
import com.yahoo.vespa.config.server.filedistribution.FileServer;
import com.yahoo.vespa.config.server.host.ConfigRequestHostLivenessTracker;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.tenant.MockTenantProvider;
import org.junit.After;
import org.junit.Before;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.yahoo.vespa.config.server.SuperModelRequestHandlerTest.emptyNodeFlavors;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Test running rpc server.
 *
 * @author lulf
 * @since 5.17
 */
// TODO: Make this a Tester instead of a superclass
public class TestWithRpc {

    private final ManualClock clock = new ManualClock(Instant.ofEpochMilli(100));
    private final String myHostname = HostName.getLocalhost();
    private final HostLivenessTracker hostLivenessTracker = new ConfigRequestHostLivenessTracker(clock);

    protected RpcServer rpcServer;
    protected MockTenantProvider tenantProvider;
    protected GenerationCounter generationCounter;
    private Thread t;
    private Supervisor sup;
    private Spec spec;
    private int port;

    private List<Integer> allocatedPorts;

    @Before
    public void setupRpc() throws InterruptedException {
        allocatedPorts = new ArrayList<>();
        port = allocatePort();
        spec = createSpec(port);
        tenantProvider = new MockTenantProvider();
        generationCounter = new MemoryGenerationCounter();
        createAndStartRpcServer(false);
        assertFalse(hostLivenessTracker.lastRequestFrom(myHostname).isPresent());
    }

    @After
    public void teardownPortAllocator() {
        for (Integer port : allocatedPorts) {
            PortRangeAllocator.releasePort(port);
        }
    }

    protected int allocatePort() throws InterruptedException {
        int port = PortRangeAllocator.findAvailablePort();
        allocatedPorts.add(port);
        return port;
    }

    protected void createAndStartRpcServer(boolean hostedVespa) {
        ConfigserverConfig configserverConfig = new ConfigserverConfig(new ConfigserverConfig.Builder());
        rpcServer = new RpcServer(new ConfigserverConfig(new ConfigserverConfig.Builder()
                                                                 .rpcport(port)
                                                                 .numRpcThreads(1)
                                                                 .maxgetconfigclients(1)
                                                                 .hostedVespa(hostedVespa)),
                                  new SuperModelRequestHandler(new TestConfigDefinitionRepo(),
                                                               configserverConfig,
                                                               new SuperModelManager(
                                                                       configserverConfig,
                                                                       emptyNodeFlavors(),
                                                                       generationCounter)),
                                  Metrics.createTestMetrics(), new HostRegistries(),
                                  hostLivenessTracker, new FileServer(Files.createTempDir()));
        rpcServer.onTenantCreate(TenantName.from("default"), tenantProvider);
        t = new Thread(rpcServer);
        t.start();
        sup = new Supervisor(new Transport());
        pingServer();
    }

    @After
    public void stopRpc() throws InterruptedException {
        rpcServer.stop();
        t.join();
    }

    private Spec createSpec(int port) {
        return new Spec("tcp/localhost:" + port);
    }

    private void pingServer() {
        long endTime = System.currentTimeMillis() + 60_000;
        Request req = new Request("ping");
        while (System.currentTimeMillis() < endTime) {
            performRequest(req);
            if (!req.isError() && req.returnValues().size() > 0 && req.returnValues().get(0).asInt32() == 0) {
                break;
            }
            req = new Request("ping");
        }
        assertFalse(req.isError());
        assertTrue(req.returnValues().size() > 0);
        assertThat(req.returnValues().get(0).asInt32(), is(0));
    }

    protected void performRequest(Request req) {
        clock.advance(Duration.ofMillis(10));
        sup.connect(spec).invokeSync(req, 120.0);
        if (req.methodName().equals(RpcServer.getConfigMethodName))
            assertEquals(clock.instant(), hostLivenessTracker.lastRequestFrom(myHostname).get());
    }
}
