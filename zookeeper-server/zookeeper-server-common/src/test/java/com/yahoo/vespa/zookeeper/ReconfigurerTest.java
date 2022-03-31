// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.net.HostName;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Phaser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Tests dynamic reconfiguration of zookeeper cluster.
 *
 * @author hmusum
 */
public class ReconfigurerTest {

    private File cfgFile;
    private File idFile;
    private TestableReconfigurer reconfigurer;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        cfgFile = folder.newFile();
        idFile = folder.newFile("myid");
        reconfigurer = new TestableReconfigurer(new TestableVespaZooKeeperAdmin());
    }

    @Test
    public void testReconfigure() {
        ZookeeperServerConfig initialConfig = createConfig(3, true);
        reconfigurer.startOrReconfigure(initialConfig);
        assertSame(initialConfig, reconfigurer.activeConfig());

        // Cluster grows
        ZookeeperServerConfig nextConfig = createConfig(5, true);
        reconfigurer.startOrReconfigure(nextConfig);
        assertEquals("node1:2181", reconfigurer.connectionSpec());
        assertEquals("server.0=node0:2182:2183;2181,server.1=node1:2182:2183;2181,server.2=node2:2182:2183;2181,server.3=node3:2182:2183;2181,server.4=node4:2182:2183;2181",
                     reconfigurer.servers());
        assertEquals(2, reconfigurer.reconfigurations());
        assertSame(nextConfig, reconfigurer.activeConfig());

        // Cluster shrinks
        nextConfig = createConfig(3, true);
        reconfigurer.startOrReconfigure(nextConfig);
        assertEquals(3, reconfigurer.reconfigurations());
        assertEquals("node1:2181", reconfigurer.connectionSpec());
        assertEquals("server.0=node0:2182:2183;2181,server.1=node1:2182:2183;2181,server.2=node2:2182:2183;2181",
                     reconfigurer.servers());
        assertSame(nextConfig, reconfigurer.activeConfig());

        // Cluster loses node1, but node3 joins. Indices are shuffled.
        nextConfig = createConfig(3, true, 1);
        reconfigurer.startOrReconfigure(nextConfig);
        assertEquals(4, reconfigurer.reconfigurations());
        assertEquals("server.0=node0:2182:2183;2181,server.1=node2:2182:2183;2181,server.2=node3:2182:2183;2181",
                     reconfigurer.servers());
        assertSame(nextConfig, reconfigurer.activeConfig());
    }

    @Test
    public void testReconfigureFailsWithReconfigInProgressThenSucceeds() {
        reconfigurer = new TestableReconfigurer(new TestableVespaZooKeeperAdmin().failures(3));
        ZookeeperServerConfig initialConfig = createConfig(3, true);
        reconfigurer.startOrReconfigure(initialConfig);
        assertSame(initialConfig, reconfigurer.activeConfig());

        ZookeeperServerConfig nextConfig = createConfig(5, true);
        reconfigurer.startOrReconfigure(nextConfig);
        assertEquals("node1:2181", reconfigurer.connectionSpec());
        assertEquals("server.0=node0:2182:2183;2181,server.1=node1:2182:2183;2181,server.2=node2:2182:2183;2181,server.3=node3:2182:2183;2181,server.4=node4:2182:2183;2181",
                     reconfigurer.servers());
        assertEquals(2, reconfigurer.reconfigurations());
        assertSame(nextConfig, reconfigurer.activeConfig());
    }

    @Test
    public void testDynamicReconfigurationDisabled() {
        ZookeeperServerConfig initialConfig = createConfig(3, false);
        reconfigurer.startOrReconfigure(initialConfig);
        assertSame(initialConfig, reconfigurer.activeConfig());

        ZookeeperServerConfig nextConfig = createConfig(5, false);
        reconfigurer.startOrReconfigure(nextConfig);
        assertSame(initialConfig, reconfigurer.activeConfig());
        assertEquals(0, reconfigurer.reconfigurations());
    }

    @After
    public void stopReconfigurer() {
       reconfigurer.shutdown();
    }

    private ZookeeperServerConfig createConfig(int numberOfServers, boolean dynamicReconfiguration, int... retiredIndices) {
        Arrays.sort(retiredIndices);
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        builder.zooKeeperConfigFile(cfgFile.getAbsolutePath());
        builder.myidFile(idFile.getAbsolutePath());
        for (int i = 0, index = 0; i < numberOfServers; i++, index++) {
            boolean retired = Arrays.binarySearch(retiredIndices, index) >= 0;
            if (retired) i--;
            builder.server(newServer(i, "node" + index, retired));
        }

        builder.myid(0);
        builder.dynamicReconfiguration(dynamicReconfiguration);
        return builder.build();
    }

    private ZookeeperServerConfig.Server.Builder newServer(int id, String hostName, boolean retired) {
        ZookeeperServerConfig.Server.Builder builder = new ZookeeperServerConfig.Server.Builder();
        builder.id(id);
        builder.hostname(hostName);
        builder.retired(retired);
        return builder;
    }

    private static class MockQuorumPeer implements QuorumPeer {
        final Phaser phaser = new Phaser(2); // Runner and test thread.
        @Override public void start(Path path) { assertEquals(1, phaser.arriveAndAwaitAdvance()); }
        @Override public void shutdown(Duration timeout) { assertEquals(1, phaser.arriveAndAwaitAdvance()); }
    }

    private static class TestableReconfigurer extends Reconfigurer implements VespaZooKeeperServer {

        private final TestableVespaZooKeeperAdmin zooKeeperAdmin;
        private final Phaser phaser = new Phaser(2);
        private QuorumPeer serverPeer;

        TestableReconfigurer(TestableVespaZooKeeperAdmin zooKeeperAdmin) {
            super(zooKeeperAdmin, new Sleeper() {
                @Override
                public void sleep(Duration duration) {
                    // Do nothing
                }
            });
            this.zooKeeperAdmin = zooKeeperAdmin;
            HostName.setHostNameForTestingOnly("node1");
        }

        void startOrReconfigure(ZookeeperServerConfig newConfig) {
            serverPeer = startOrReconfigure(newConfig, this, MockQuorumPeer::new);
            phaser.arriveAndDeregister();
        }

        String connectionSpec() {
            return zooKeeperAdmin.connectionSpec;
        }

        String servers() {
            return zooKeeperAdmin.servers;
        }

        int reconfigurations() {
            return zooKeeperAdmin.reconfigurations;
        }

        @Override
        public void shutdown() {
            phaser.arriveAndAwaitAdvance();
            serverPeer.shutdown(Duration.ofSeconds(1)); }

        @Override
        public void start(Path configFilePath) {
            phaser.arriveAndAwaitAdvance();
            serverPeer.start(configFilePath);
        }

        @Override
        public boolean reconfigurable() {
            return true;
        }

    }

    private static class TestableVespaZooKeeperAdmin implements VespaZooKeeperAdmin {

        String connectionSpec;
        String servers;
        int reconfigurations = 0;

        private int failures = 0;
        private int attempts = 0;

        public TestableVespaZooKeeperAdmin failures(int failures) {
            this.failures = failures;
            return this;
        }

        @Override
        public void reconfigure(String connectionSpec, String servers) throws ReconfigException {
            if (++attempts < failures)
                throw new ReconfigException("Reconfig failed");
            this.connectionSpec = connectionSpec;
            this.servers = servers;
            this.reconfigurations++;
        }

    }


}
