// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.monitoring;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import org.junit.After;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ZKMetricUpdaterTest {
    private Thread serverThread;
    private int serverPort;

    @After
    public void terminate() throws InterruptedException {
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread.join(500);
        }
    }

    @Test
    public void zk_monitoring_data_is_parsed_and_reported() throws IOException {
        setupTcpServer(() -> "zk_version\t3.4.0\n" + //
                "zk_avg_latency\t444\n" + //
                "zk_max_latency\t1234\n" + //
                "zk_min_latency\t0\n" + //
                "zk_packets_received\t71\n" + //
                "zk_packets_sent\t70\n" + //
                "zk_outstanding_requests\t12\n" + //
                "zk_num_alive_connections\t2\n" + //
                "zk_server_state\tleader\n" + //
                "zk_znode_count\t4\n" + //
                "zk_watch_count\t0\n" + //
                "zk_ephemerals_count\t0\n" + //
                "zk_approximate_data_size\t27\n");

        ZKMetricUpdater updater = buildUpdater();
        updater.run();

        Map<String, Long> reportedMetrics = updater.getZKMetrics();

        assertThat(reportedMetrics.get(ZKMetricUpdater.METRIC_ZK_CONNECTIONS), equalTo(2L));
        assertThat(reportedMetrics.get(ZKMetricUpdater.METRIC_ZK_LATENCY_AVERAGE), equalTo(444L));
        assertThat(reportedMetrics.get(ZKMetricUpdater.METRIC_ZK_LATENCY_MAX), equalTo(1234L));
        assertThat(reportedMetrics.get(ZKMetricUpdater.METRIC_ZK_OUTSTANDING_REQUESTS), equalTo(12L));
        assertThat(reportedMetrics.get(ZKMetricUpdater.METRIC_ZK_ZNODES), equalTo(4L));

        updater.shutdown();
    }

    private ZKMetricUpdater buildUpdater() {
        ZookeeperServerConfig zkServerConfig = new ZookeeperServerConfig(
                new ZookeeperServerConfig.Builder().clientPort(serverPort).myid(12345));
        return new ZKMetricUpdater(zkServerConfig, 0, 100000);
    }

    private void setupTcpServer(Supplier<String> reportProvider) throws IOException {
        ServerSocket serverSocket = new ServerSocket(0);
        serverPort = serverSocket.getLocalPort();
        serverThread = Executors.defaultThreadFactory().newThread(() -> {
            while (!Thread.interrupted()) {
                try (Socket connection = serverSocket.accept()) {
                    BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String verb = input.readLine();
                    if ("mntr".equals(verb)) {
                        DataOutputStream output = new DataOutputStream(connection.getOutputStream());
                        output.write(reportProvider.get().getBytes(StandardCharsets.UTF_8));
                        output.close();
                    }
                } catch (IOException e) {
                    System.out.println("Error in fake ZK server: " + e.toString());
                }
            }
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.out.println("Error closing server socket in fake ZK server: " + e.toString());
            }
        });
        serverThread.start();
    }
}
