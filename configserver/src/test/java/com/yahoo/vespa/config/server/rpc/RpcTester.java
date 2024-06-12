// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.MemoryGenerationCounter;
import com.yahoo.vespa.config.server.PortRangeAllocator;
import com.yahoo.vespa.config.server.SuperModelManager;
import com.yahoo.vespa.config.server.SuperModelRequestHandler;
import com.yahoo.vespa.config.server.TestConfigDefinitionRepo;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.filedistribution.FileDirectory;
import com.yahoo.vespa.config.server.filedistribution.FileServer;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.rpc.security.NoopRpcAuthorizer;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import org.junit.After;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tester for running rpc server.
 *
 * @author Ulf Lilleengen
 */
public class RpcTester implements AutoCloseable {

    private final ManualClock clock = new ManualClock(Instant.ofEpochMilli(100));
    private final Spec spec;

    private final RpcServer rpcServer;
    private Thread t;
    private Supervisor sup;
    private final ApplicationId applicationId;
    private final TenantName tenantName;
    final HostRegistry hostRegistry = new HostRegistry();

    private final ApplicationRepository applicationRepository;
    private final List<Integer> allocatedPorts = new ArrayList<>();
    private final TemporaryFolder temporaryFolder;

    RpcTester(ApplicationId applicationId, TemporaryFolder temporaryFolder) throws InterruptedException, IOException {
        this(applicationId, temporaryFolder, new ConfigserverConfig.Builder());
    }

    RpcTester(ApplicationId applicationId, TemporaryFolder temporaryFolder, ConfigserverConfig.Builder configBuilder) throws InterruptedException, IOException {
        this.temporaryFolder = temporaryFolder;
        this.applicationId = applicationId;
        this.tenantName = applicationId.tenant();

        Spec tempSpec;
        ConfigserverConfig tempConfig;
        RpcServer tempRpcServer;
        TenantRepository tempTenantRepository;

        int iterations = 0;
        // Need to loop because we might get a port that is in use
        do {
            int port = allocatePort();
            tempSpec = createSpec(port);
            configBuilder.rpcport(port)
                    .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                    .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                    .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath());
            tempConfig = new ConfigserverConfig(configBuilder);
            tempRpcServer = createRpcServer(tempConfig);
            tempTenantRepository = new TestTenantRepository.Builder()
                    .withHostRegistry(hostRegistry)
                    .withConfigserverConfig(tempConfig)
                    .build();
            tempTenantRepository.addTenant(tenantName);
            startRpcServer(tempRpcServer, tempTenantRepository, tempSpec);
            iterations++;
        } while (!tempRpcServer.isRunning() && iterations < 10);

        assertTrue("server is not running", tempRpcServer.isRunning());

        spec = tempSpec;
        ConfigserverConfig configserverConfig = tempConfig;
        rpcServer = tempRpcServer;
        TenantRepository tenantRepository = tempTenantRepository;

        applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withConfigserverConfig(configserverConfig)
                .withOrchestrator(new OrchestratorMock())
                .build();
    }

    public void close() throws InterruptedException {
        rpcServer.stop();
        for (Integer port : allocatedPorts) {
            PortRangeAllocator.releasePort(port);
        }
        t.join();
    }

    private int allocatePort() throws InterruptedException {
        int port = PortRangeAllocator.findAvailablePort();
        allocatedPorts.add(port);
        return port;
    }

    RpcServer createRpcServer(ConfigserverConfig config) throws IOException {
        InMemoryFlagSource flagSource = new InMemoryFlagSource();
        RpcServer rpcServer = new RpcServer(config,
                                            new SuperModelRequestHandler(new TestConfigDefinitionRepo(),
                                                          config,
                                                          new SuperModelManager(
                                                                  config,
                                                                  Zone.defaultZone(),
                                                                  new MemoryGenerationCounter(),
                                                                  flagSource)),
                                            Metrics.createTestMetrics(),
                                            hostRegistry,
                                            new FileServer(config, new FileDirectory(temporaryFolder.newFolder())),
                                            new NoopRpcAuthorizer(),
                                            new RpcRequestHandlerProvider());
        rpcServer.setUpGetConfigHandlers();
        return rpcServer;
    }

    void startRpcServer(RpcServer rpcServer, TenantRepository tenantRepository, Spec spec)  {
        hostRegistry.update(applicationId, List.of("localhost"));
        rpcServer.onTenantCreate(tenantRepository.getTenant(tenantName));
        t = new Thread(rpcServer);
        t.start();
        sup = new Supervisor(new Transport());
        pingServer(spec);
    }

    private Spec createSpec(int port) {
        return new Spec("tcp/localhost:" + port);
    }

    private void pingServer(Spec spec) {
        long endTime = System.currentTimeMillis() + 60_000;
        Request req = new Request("ping");
        while (System.currentTimeMillis() < endTime) {
            performRequest(req, spec);
            if (!req.isError() && req.returnValues().size() > 0 && req.returnValues().get(0).asInt32() == 0) {
                break;
            }
            req = new Request("ping");
        }
        assertFalse(req.isError());
        assertTrue(req.returnValues().size() > 0);
        assertEquals(0, req.returnValues().get(0).asInt32());
    }

    void performRequest(Request req) {
        performRequest(req, spec);
    }

    void performRequest(Request req, Spec spec) {
        clock.advance(Duration.ofMillis(10));
        sup.connect(spec).invokeSync(req, Duration.ofSeconds(10));
    }

    RpcServer rpcServer() {
        return rpcServer;
    }

    public ApplicationRepository applicationRepository() { return applicationRepository; }

}
