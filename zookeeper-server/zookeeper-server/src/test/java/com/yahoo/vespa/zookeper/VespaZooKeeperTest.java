// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.net.HostName;
import com.yahoo.vespa.zookeeper.ReconfigurableVespaZooKeeperServer;
import com.yahoo.vespa.zookeeper.Reconfigurer;
import com.yahoo.vespa.zookeeper.VespaZooKeeperAdminImpl;
import com.yahoo.vespa.zookeeper.client.ZkClientConfigBuilder;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.admin.ZooKeeperAdmin;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class VespaZooKeeperTest {

    static final Path tempDirRoot = getTmpDir();
    static final List<Integer> ports = new ArrayList<>();

    /**
     * Performs dynamic reconfiguration of ZooKeeper servers.
     * <p>
     * First, a cluster of 3 servers is set up, and some data is written to it.
     * Then, 3 new servers are added, and the first 3 marked for retirement;
     * this should force the quorum to move the 3 new servers, but not disconnect the old ones.
     * Next, the old servers are removed.
     * Then, the cluster is reduced to size 1.
     * Finally, the cluster grows to size 3 again.
     * <p>
     * Throughout all of this, quorum should remain, and the data should remain the same.
     */
    @Test(timeout = 120_000)
    public void testReconfiguration() throws ExecutionException, InterruptedException, IOException, KeeperException, TimeoutException {
        List<ZooKeeper> keepers = new ArrayList<>();
        for (int i = 0; i < 8; i++) keepers.add(new ZooKeeper());
        for (int i = 0; i < 8; i++) keepers.get(i).run();

        // Start the first three servers.
        List<ZookeeperServerConfig> configs = getConfigs(0, 0, 3, 0);
        for (int i = 0; i < 3; i++) keepers.get(i).config = configs.get(i);
        for (int i = 0; i < 3; i++) keepers.get(i).phaser.arriveAndAwaitAdvance();

        // Wait for all servers to be up and running.
        for (int i = 0; i < 3; i++) keepers.get(i).phaser.arriveAndAwaitAdvance();

        // Write data to verify later.
        String path = writeData(configs.get(0));

        // Let three new servers join, causing the three older ones to retire and leave the ensemble.
        configs = getConfigs(0, 3, 3, 3);
        for (int i = 0; i < 6; i++) keepers.get(i).config = configs.get(i);
        // The existing servers can't reconfigure and leave before the joiners are up.
        for (int i = 0; i < 6; i++) keepers.get(i).phaser.arriveAndAwaitAdvance();

        // Wait for new quorum to be established.
        for (int i = 0; i < 6; i++) keepers.get(i).phaser.arriveAndAwaitAdvance();

        // Verify written data is preserved.
        verifyData(path, configs.get(3));

        // Old servers are removed.
        configs = getConfigs(3, 0, 3, 0);
        for (int i = 0; i < 6; i++) keepers.get(i).config = configs.get(i);
        // Old servers shut down, while the newer servers remain.
        for (int i = 0; i < 6; i++) keepers.get(i).phaser.arriveAndAwaitAdvance();
        // Ensure old servers shut down properly.
        for (int i = 0; i < 3; i++) keepers.get(i).await();
        // Ensure new servers have reconfigured.
        for (int i = 3; i < 6; i++) keepers.get(i).phaser.arriveAndAwaitAdvance();

        // Verify written data is preserved.
        verifyData(path, configs.get(3));


        // Cluster shrinks to a single server.
        configs = getConfigs(5, 0, 1, 0);
        for (int i = 3; i < 6; i++) keepers.get(i).config = configs.get(i);
        for (int i = 5; i < 6; i++) keepers.get(i).phaser.arriveAndAwaitAdvance();
        for (int i = 5; i < 6; i++) keepers.get(i).phaser.arriveAndAwaitAdvance();
        // We let the remaining server reconfigure the others out before they die.
        for (int i = 3; i < 5; i++) keepers.get(i).phaser.arriveAndAwaitAdvance();
        for (int i = 3; i < 5; i++) keepers.get(i).await();
        verifyData(path, configs.get(5));

        // Cluster grows to 3 servers again.
        configs = getConfigs(5, 0, 3, 2);
        for (int i = 5; i < 8; i++) keepers.get(i).config = configs.get(i);
        for (int i = 5; i < 8; i++) keepers.get(i).phaser.arriveAndAwaitAdvance();
        // Wait for the joiners.
        for (int i = 5; i < 8; i++) keepers.get(i).phaser.arriveAndAwaitAdvance();
        verifyData(path, configs.get(7));

        // Let the remaining servers terminate.
        for (int i = 5; i < 8; i++) keepers.get(i).config = null;
        for (int i = 5; i < 8; i++) keepers.get(i).phaser.arriveAndAwaitAdvance();
        for (int i = 5; i < 8; i++) keepers.get(i).await();
    }

    static String writeData(ZookeeperServerConfig config) throws IOException, InterruptedException, KeeperException {
        try (ZooKeeperAdmin admin = createAdmin(config)) {
            List<ACL> acl = ZooDefs.Ids.OPEN_ACL_UNSAFE;
            String node = admin.create("/test-node", "hi".getBytes(UTF_8), acl, CreateMode.PERSISTENT_SEQUENTIAL);
            String read = new String(admin.getData(node, false, new Stat()), UTF_8);
            assertEquals("hi", read);
            return node;
        }
    }

    static void verifyData(String path, ZookeeperServerConfig config) throws IOException, InterruptedException, KeeperException {
        for (int i = 0; i < 10; i++) {
            try (ZooKeeperAdmin admin = createAdmin(config)) {
                assertEquals("hi", new String(admin.getData(path, false, new Stat()), UTF_8));
                return;
            }
            catch (KeeperException.ConnectionLossException e) {
                e.printStackTrace();
                Thread.sleep(10 << i);
            }
        }
    }

    static ZooKeeperAdmin createAdmin(ZookeeperServerConfig config) throws IOException {
        return new ZooKeeperAdmin(HostName.getLocalhost() + ":" + config.clientPort(),
                                  10_000,
                                  System.err::println,
                                  new ZkClientConfigBuilder().toConfig());
    }

    static class ZooKeeper {

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Phaser phaser = new Phaser(2);
        final AtomicReference<Future<?>> future = new AtomicReference<>();
        ZookeeperServerConfig config;

        void run() {
            future.set(executor.submit(() -> {
                Reconfigurer reconfigurer = new Reconfigurer(new VespaZooKeeperAdminImpl());
                phaser.arriveAndAwaitAdvance();
                while (config != null) {
                    new ReconfigurableVespaZooKeeperServer(reconfigurer, config);
                    phaser.arriveAndAwaitAdvance(); // server is now up, let test thread sync here
                    phaser.arriveAndAwaitAdvance(); // wait before reconfig/teardown to let test thread do stuff
                }
                reconfigurer.deconstruct();
            }));
        }

        void await() throws ExecutionException, InterruptedException, TimeoutException {
            future.get().get(30, SECONDS);
        }
    }

    static List<ZookeeperServerConfig> getConfigs(int removed, int retired, int active, int joining) {
        return IntStream.rangeClosed(1, removed + retired + active)
                        .mapToObj(id -> getConfig(removed, retired, active, joining, id))
                        .collect(toList());
    }

    // Config for server #id among retired + active servers, of which the last may be joining, and with offset removed.
    static ZookeeperServerConfig getConfig(int removed, int retired, int active, int joining, int id) {
        if (id <= removed)
            return null;

        Path tempDir = tempDirRoot.resolve("zookeeper-" + id);
        return new ZookeeperServerConfig.Builder()
                .clientPort(getPorts(id).get(0))
                .dataDir(tempDir.toString())
                .zooKeeperConfigFile(tempDir.resolve("zookeeper.cfg").toString())
                .myid(id)
                .myidFile(tempDir.resolve("myid").toString())
                .dynamicReconfiguration(true)
                .server(IntStream.rangeClosed(removed + 1, removed + retired + active)
                                 .mapToObj(i -> new ZookeeperServerConfig.Server.Builder()
                                         .id(i)
                                         .clientPort(getPorts(i).get(0))
                                         .electionPort(getPorts(i).get(1))
                                         .quorumPort(getPorts(i).get(2))
                                         .hostname("localhost")
                                         .joining(i - removed > retired + active - joining)
                                         .retired(i - removed <= retired))
                                 .collect(toList()))
                .build();
    }

    static List<Integer> getPorts(int id) {
        if (ports.size() < id * 3) {
            int previousPort;
            if (ports.isEmpty()) {
                String[] version = System.getProperty("zk-version").split("\\.");
                int versionPortOffset = 0;
                for (String part : version)
                    versionPortOffset = 32 * (versionPortOffset + Integer.parseInt(part));
                previousPort = 20000 + versionPortOffset % 30000;
            }
            else
                previousPort = ports.get(ports.size() - 1);

            for (int i = 0; i < 3; i++)
                ports.add(previousPort = nextPort(previousPort));
        }
        return ports.subList(id * 3 - 3, id * 3);
    }

    static int nextPort(int previousPort) {
        for (int j = 1; j <= 30000; j++) {
            int port = (previousPort + j);
            while (port > 50000)
                port -= 30000;

            try (ServerSocket socket = new ServerSocket(port)) {
                return socket.getLocalPort();
            }
            catch (IOException e) {
                System.err.println("Could not bind port " + port + ": " + e);
            }
        }
        throw new RuntimeException("No free ports");
    }

    static Path getTmpDir() {
        try {
            Path tempDir = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), "vespa-zk-test");
            tempDir.toFile().deleteOnExit();
            return tempDir.toAbsolutePath();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
