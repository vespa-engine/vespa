// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.net.HostName;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.zookeeper.VespaZooKeeperServer;
import com.yahoo.vespa.zookeeper.VespaZooKeeperServerImpl;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Optional;

/**
 * This class sets up a zookeeper server, such that we can test fleetcontroller zookeeper parts without stubbing in the client.
 */
public class ZooKeeperTestServer {
    private final File zooKeeperDir;
    private final VespaZooKeeperServer server;
    private static final Duration tickTime = Duration.ofMillis(2000);
    private static final String DIR_PREFIX = "test_fltctrl_zk";
    private static final String DIR_POSTFIX = "sdir";
    private final int port;

    ZooKeeperTestServer() throws IOException {
        this(0);
    }

    private ZooKeeperTestServer(int wantedPort) throws IOException {
        this.port = findFreePort(wantedPort);
        zooKeeperDir = getTempDir();
        delete(zooKeeperDir);
        if (!zooKeeperDir.mkdir()) {
            throw new IllegalStateException("Failed to create directory " + zooKeeperDir);
        }
        zooKeeperDir.deleteOnExit();
        ZookeeperServerConfig config = new ZookeeperServerConfig.Builder()
                .tickTime((int) tickTime.toMillis())
                .clientPort(this.port)
                .server(new ZookeeperServerConfig.Server.Builder().hostname("localhost").id(0))
                .myid(0)
                .zooKeeperConfigFile(zooKeeperDir.toPath().resolve("zookeeper.cfg").toFile().getAbsolutePath())
                .dataDir(zooKeeperDir.toPath().toFile().getAbsolutePath())
                .myidFile(zooKeeperDir.toPath().resolve("myId").toFile().getAbsolutePath())
                .build();
        server = new VespaZooKeeperServerImpl(config);

        try (Curator curator = Curator.create("localhost:" + port, Optional.empty())) {
            try {
                curator.framework().blockUntilConnected();
            } catch (InterruptedException interruptedException) {
                throw new RuntimeException(interruptedException);
            }
        }
    }

    static ZooKeeperTestServer createWithFixedPort(int port) throws IOException {
        return new ZooKeeperTestServer(port);
    }

    private int findFreePort(int port) {
        if (port != 0) return port;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new NullPointerException("Could not find any free port");
        }
    }

    private int getPort() { return port; }

    String getAddress() {
        return HostName.getLocalhost() + ":" + getPort();
    }

    public void shutdown(boolean cleanupZooKeeperDir) {
        server.shutdown();

        if (cleanupZooKeeperDir) {
            delete(zooKeeperDir);
        }
    }

    private void delete(File f) {
        if (f.isDirectory()) {
            for (File file : f.listFiles()) {
                delete(file);
            }
        }
        f.delete();
    }

    private static File getTempDir() throws IOException {
        // The pom file sets java.io.tmpdir to ${project.build.directory}. This doesn't happen within (e.g.) IntelliJ, but happens
        // on Screwdriver (tm). So if we're running tests on Screwdriver (tm), put the log in 'surefire-reports' instead so the
        // user can find them along with the other test reports.
        final File surefireReportsDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "surefire-reports");
        if (surefireReportsDir.isDirectory()) {
            return File.createTempFile(DIR_PREFIX, DIR_POSTFIX, surefireReportsDir);
        }

        return File.createTempFile(DIR_PREFIX, DIR_POSTFIX);
    }

}
