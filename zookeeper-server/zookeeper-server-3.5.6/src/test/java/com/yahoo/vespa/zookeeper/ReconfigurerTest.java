// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import org.apache.zookeeper.KeeperException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
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
        reconfigurer = new TestableReconfigurer();
    }

    @Test
    public void testReconfigure() {
        ZookeeperServerConfig initialConfig = createConfig(3, true);
        reconfigurer.startOrReconfigure(initialConfig);
        assertSame(initialConfig, reconfigurer.activeConfig());

        // Cluster grows
        ZookeeperServerConfig nextConfig = createConfig(5, true);
        reconfigurer.startOrReconfigure(nextConfig);
        assertEquals("node0:2181,node1:2181,node2:2181", reconfigurer.connectionSpec);
        assertEquals("3=node3:2182:2183;2181,4=node4:2182:2183;2181", reconfigurer.joiningServers);
        assertNull("No servers are leaving", reconfigurer.leavingServers);
        assertEquals(1, reconfigurer.reconfigurations);
        assertSame(nextConfig, reconfigurer.activeConfig());

        // No reconfiguration happens with same config
        reconfigurer.startOrReconfigure(nextConfig);
        assertEquals(1, reconfigurer.reconfigurations);
        assertSame(nextConfig, reconfigurer.activeConfig());

        // Cluster shrinks
        nextConfig = createConfig(3, true);
        reconfigurer.startOrReconfigure(nextConfig);
        assertEquals(2, reconfigurer.reconfigurations);
        assertEquals("node0:2181,node1:2181,node2:2181,node3:2181,node4:2181", reconfigurer.connectionSpec);
        assertNull("No servers are joining", reconfigurer.joiningServers);
        assertEquals("3,4", reconfigurer.leavingServers);
        assertSame(nextConfig, reconfigurer.activeConfig());
    }

    @Test
    public void testReconfigureFailsWithReconfigInProgressThenSucceeds() {
        reconfigurer = new TemporarilyFailWithReconfigInProgressReconfigurer();
        ZookeeperServerConfig initialConfig = createConfig(3, true);
        reconfigurer.startOrReconfigure(initialConfig);
        assertSame(initialConfig, reconfigurer.activeConfig());

        ZookeeperServerConfig nextConfig = createConfig(5, true);
        reconfigurer.startOrReconfigure(nextConfig);
        assertEquals("node0:2181,node1:2181,node2:2181", reconfigurer.connectionSpec);
        assertEquals("3=node3:2182:2183;2181,4=node4:2182:2183;2181", reconfigurer.joiningServers);
        assertNull("No servers are leaving", reconfigurer.leavingServers);
        assertEquals(1, reconfigurer.reconfigurations);
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
        assertEquals(0, reconfigurer.reconfigurations);
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

    private static class TestableReconfigurer extends Reconfigurer {

        private int reconfigurations = 0;

        private String connectionSpec;
        private String joiningServers;
        private String leavingServers;

        @Override
        void zooKeeperReconfigure(String connectionSpec, String joiningServers, String leavingServers) throws KeeperException {
            this.connectionSpec = connectionSpec;
            this.joiningServers = joiningServers;
            this.leavingServers = leavingServers;
            this.reconfigurations++;
        }

    }

    // Fails 3 times with KeeperException.ReconfigInProgress(), then succeeds
    private static class TemporarilyFailWithReconfigInProgressReconfigurer extends TestableReconfigurer {

        private int attempts = 0;

        @Override
        void zooKeeperReconfigure(String connectionSpec, String joiningServers, String leavingServers) throws KeeperException {
            attempts++;
            System.out.println("attempt " + attempts + " at reconfiguring");
            if (attempts < 3)
                throw new KeeperException.ReconfigInProgress();
            else
                super.zooKeeperReconfigure(connectionSpec, joiningServers, leavingServers);
        }

    }

}
