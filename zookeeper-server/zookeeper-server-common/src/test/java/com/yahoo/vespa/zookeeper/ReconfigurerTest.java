// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
        assertEquals("3=node3:2182:2183;2181,4=node4:2182:2183;2181", reconfigurer.joiningServers());
        assertNull("No servers are leaving", reconfigurer.leavingServers());
        assertEquals(1, reconfigurer.reconfigurations());
        assertSame(nextConfig, reconfigurer.activeConfig());

        // No reconfiguration happens with same config
        reconfigurer.startOrReconfigure(nextConfig);
        assertEquals(1, reconfigurer.reconfigurations());
        assertSame(nextConfig, reconfigurer.activeConfig());

        // Cluster shrinks
        nextConfig = createConfig(3, true);
        reconfigurer.startOrReconfigure(nextConfig);
        assertEquals(2, reconfigurer.reconfigurations());
        assertEquals("node1:2181", reconfigurer.connectionSpec());
        assertNull("No servers are joining", reconfigurer.joiningServers());
        assertEquals("3,4", reconfigurer.leavingServers());
        assertSame(nextConfig, reconfigurer.activeConfig());
    }

    @Test
    public void testReconfigureFailsWithReconfigInProgressThenSucceeds() {
        try {
            TestableReconfigurer reconfigurer = new TestableReconfigurer(new TestableVespaZooKeeperAdmin().failures(3));
            ZookeeperServerConfig initialConfig = createConfig(3, true);
            reconfigurer.startOrReconfigure(initialConfig);
            assertSame(initialConfig, reconfigurer.activeConfig());

            ZookeeperServerConfig nextConfig = createConfig(5, true);
            reconfigurer.startOrReconfigure(nextConfig);
            assertEquals("node1:2181", reconfigurer.connectionSpec());
            assertEquals("3=node3:2182:2183;2181,4=node4:2182:2183;2181", reconfigurer.joiningServers());
            assertNull("No servers are leaving", reconfigurer.leavingServers());
            assertEquals(1, reconfigurer.reconfigurations());
            assertSame(nextConfig, reconfigurer.activeConfig());
        } finally {
            reconfigurer.shutdown();
        }
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

    private ZookeeperServerConfig createConfig(int numberOfServers, boolean dynamicReconfiguration) {
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        builder.zooKeeperConfigFile(cfgFile.getAbsolutePath());
        builder.myidFile(idFile.getAbsolutePath());
        IntStream.range(0, numberOfServers).forEach(i -> builder.server(newServer(i, "node" + i)));
        builder.myid(0);
        builder.dynamicReconfiguration(dynamicReconfiguration);
        return builder.build();
    }

    private ZookeeperServerConfig.Server.Builder newServer(int id, String hostName) {
        ZookeeperServerConfig.Server.Builder builder = new ZookeeperServerConfig.Server.Builder();
        builder.id(id);
        builder.hostname(hostName);
        return builder;
    }

    private static class TestableReconfigurer extends Reconfigurer implements VespaZooKeeperServer {

        private final TestableVespaZooKeeperAdmin zooKeeperAdmin;

        TestableReconfigurer(TestableVespaZooKeeperAdmin zooKeeperAdmin) {
            super(zooKeeperAdmin, (ignored) -> {});
            this.zooKeeperAdmin = zooKeeperAdmin;
            HostName.setHostNameForTestingOnly("node1");
        }

        void startOrReconfigure(ZookeeperServerConfig newConfig) {
            startOrReconfigure(newConfig, this);
        }

        String connectionSpec() {
            return zooKeeperAdmin.connectionSpec;
        }

        String joiningServers() {
            return zooKeeperAdmin.joiningServers;
        }

        String leavingServers() {
            return zooKeeperAdmin.leavingServers;
        }

        int reconfigurations() {
            return zooKeeperAdmin.reconfigurations;
        }

        @Override
        public void start(Path configFilePath) { }

    }

    private static class TestableVespaZooKeeperAdmin implements VespaZooKeeperAdmin {

        String connectionSpec;
        String joiningServers;
        String leavingServers;
        int reconfigurations = 0;

        private int failures = 0;
        private int attempts = 0;

        public TestableVespaZooKeeperAdmin failures(int failures) {
            this.failures = failures;
            return this;
        }

        @Override
        public void reconfigure(String connectionSpec, String joiningServers, String leavingServers) throws ReconfigException {
            if (++attempts < failures)
                throw new ReconfigException("Reconfig failed");
            this.connectionSpec = connectionSpec;
            this.joiningServers = joiningServers;
            this.leavingServers = leavingServers;
            this.reconfigurations++;
        }

    }


}
