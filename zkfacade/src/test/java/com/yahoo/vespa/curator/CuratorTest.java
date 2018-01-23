// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.net.HostName;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * Sets up actual ZooKeeper servers and verifies we can talk to them.
 *
 * @author lulf
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
            assertThat(curator.zooKeeperEnsembleConnectionSpec(), is(spec1 + "," + spec2));
            assertThat(curator.zooKeeperEnsembleCount(), is(2));
        }
    }

    @Test
    public void require_that_server_count_is_correct() {
        ConfigserverConfig.Builder builder = new ConfigserverConfig.Builder();
        builder.zookeeperserver(createZKBuilder(localhost, port1));
        try (Curator curator = createCurator(new ConfigserverConfig(builder))) {
            assertThat(curator.zooKeeperEnsembleCount(), is(1));
        }
    }

    @Test
     public void localhost_affinity() {
         String localhostHostName = "myhost";
         int localhostPort = 123;

         ConfigserverConfig.Builder builder = new ConfigserverConfig.Builder();
         builder.zookeeperserver(createZKBuilder(localhostHostName, localhostPort));
         builder.zookeeperserver(createZKBuilder("otherhost", 345));
         ConfigserverConfig config = new ConfigserverConfig(builder);

         HostName.setHostNameForTestingOnly(localhostHostName);

        String localhostSpec = localhostHostName + ":" + localhostPort;
         assertThat(Curator.createConnectionSpecForLocalhost(config), is(localhostSpec));
     }

    private ConfigserverConfig createTestConfig() {
        ConfigserverConfig.Builder builder = new ConfigserverConfig.Builder();
        builder.zookeeperserver(createZKBuilder(localhost, port1));
        builder.zookeeperserver(createZKBuilder(localhost, port2));
        return new ConfigserverConfig(builder);
    }

    private ConfigserverConfig.Zookeeperserver.Builder createZKBuilder(String hostname, int port) {
        ConfigserverConfig.Zookeeperserver.Builder zkBuilder = new ConfigserverConfig.Zookeeperserver.Builder();
        zkBuilder.hostname(hostname);
        zkBuilder.port(port);
        return zkBuilder;
    }

    private Curator createCurator(ConfigserverConfig configserverConfig) {
        return new Curator(configserverConfig, null);
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
