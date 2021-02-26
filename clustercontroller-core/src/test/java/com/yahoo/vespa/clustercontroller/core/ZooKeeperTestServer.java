// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.net.HostName;
import com.yahoo.vespa.zookeeper.VespaZooKeeperServer;
import com.yahoo.vespa.zookeeper.VespaZooKeeperTestServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

/**
 * This class sets up a zookeeper server, such that we can test fleetcontroller zookeeper parts without stubbing in the client.
 */
public class ZooKeeperTestServer {
    private final VespaZooKeeperServer server;
    private static final Duration tickTime = Duration.ofMillis(2000);


    private final int port;

    ZooKeeperTestServer() throws IOException {
        this(0, tickTime);
    }

    ZooKeeperTestServer(int wantedPort, Duration tickTime) throws IOException {
        this.port = findFreePort(wantedPort);
        server = VespaZooKeeperTestServer.createAndStartServer(port, (int) tickTime.toMillis());
    }

    static ZooKeeperTestServer createWithFixedPort(int port) throws IOException {
        return new ZooKeeperTestServer(port, tickTime);
    }

    private int findFreePort(int port) {
        if (port != 0) return port;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new NullPointerException("Could not find any free port");
        }
    }

    public int getPort() { return port; }

    String getAddress() {
        return HostName.getLocalhost() + ":" + getPort();
    }

    public void shutdown() {
        server.shutdown();
    }

}
