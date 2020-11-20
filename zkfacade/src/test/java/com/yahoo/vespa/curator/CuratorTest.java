// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.cloud.config.CuratorConfig;
import com.yahoo.net.HostName;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * Sets up actual ZooKeeper servers and verifies we can talk to them.
 *
 * @author Ulf Lilleengen
 */
public class CuratorTest {

    private static String localhost = HostName.getLocalhost();

    private String spec1;
    private String spec2;
    private TestingServer test1;
    private TestingServer test2;
    private int port1;
    private int port2;

    @Before
    public void setupServers() throws Exception {
        port1 = allocatePort();
        port2 = allocatePort();
        test1 = new TestingServer(port1);
        test2 = new TestingServer(port2);
        spec1 = localhost + ":" + port1;
        spec2 = localhost + ":" + port2;
    }

    private int allocatePort() {
        return PortAllocator.findAvailablePort();
    }

    @After
    public void teardownServers() throws IOException {
        test1.stop();
        test1.close();
        test2.close();
        test2.stop();
    }

    @Test
    public void require_curator_is_created_from_config() {
        try (Curator curator = createCurator(createTestConfig())) {
            assertEquals(spec1 + "," + spec2, curator.zooKeeperEnsembleConnectionSpec());
            assertEquals(2, curator.zooKeeperEnsembleCount());
        }
    }

    @Test
    public void require_that_server_count_is_correct() {
        CuratorConfig.Builder builder = new CuratorConfig.Builder();
        builder.server(createZKBuilder(localhost, port1));
        try (Curator curator = createCurator(new CuratorConfig(builder))) {
            assertEquals(1, curator.zooKeeperEnsembleCount());
        }
    }

    private CuratorConfig createTestConfig() {
        CuratorConfig.Builder builder = new CuratorConfig.Builder();
        builder.server(createZKBuilder(localhost, port1));
        builder.server(createZKBuilder(localhost, port2));
        return new CuratorConfig(builder);
    }

    private CuratorConfig.Server.Builder createZKBuilder(String hostname, int port) {
        CuratorConfig.Server.Builder zkBuilder = new CuratorConfig.Server.Builder();
        zkBuilder.hostname(hostname);
        zkBuilder.port(port);
        return zkBuilder;
    }

    private Curator createCurator(CuratorConfig curatorConfig)  {
        return new Curator(ConnectionSpec.create(curatorConfig.server(),
                                                 CuratorConfig.Server::hostname,
                                                 CuratorConfig.Server::port,
                                                 curatorConfig.zookeeperLocalhostAffinity()),
                           Optional.empty());
    }

    private static class PortAllocator {

        private static class PortRange {
            private int first = 18621;
            private int last = 18630; // see: factory/doc/port-ranges
            private int value = first;

            synchronized int next() {
                if (value > last) {
                    throw new RuntimeException("no port ports in range");
                }
                return value++;
            }
        }

        private final static PortRange portRange = new PortRange();

        // Get the next port from a pre-allocated range
        static int findAvailablePort() {
            return portRange.next();
        }

    }

}
