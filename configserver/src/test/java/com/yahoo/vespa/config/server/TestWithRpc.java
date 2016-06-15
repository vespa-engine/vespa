// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.ElkConfig;
import com.yahoo.config.provision.TenantName;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.vespa.config.GenerationCounter;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Test running rpc server.
 *
 * @author lulf
 * @since 5.17
 */
public class TestWithRpc {

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
        rpcServer = new RpcServer(new ConfigserverConfig(new ConfigserverConfig.Builder().rpcport(port).numthreads(1).maxgetconfigclients(1).hostedVespa(hostedVespa)),
                new SuperModelController(generationCounter,
                        new TestConfigDefinitionRepo(), new ConfigserverConfig(new ConfigserverConfig.Builder()), new ElkConfig(new ElkConfig.Builder())),
                Metrics.createTestMetrics(), new HostRegistries());
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
        sup.connect(spec).invokeSync(req, 120.0);
    }
}
