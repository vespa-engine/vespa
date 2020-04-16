// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.monitoring;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.concurrent.DaemonThreadFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.yahoo.vespa.config.server.monitoring.Metrics.getMetricName;

public class ZKMetricUpdater implements Runnable {
    private static final Logger log = Logger.getLogger(ZKMetricUpdater.class.getName());

    public static final String METRIC_ZK_ZNODES = getMetricName("zkZNodes");
    public static final String METRIC_ZK_LATENCY_AVERAGE = getMetricName("zkAvgLatency");
    public static final String METRIC_ZK_LATENCY_MAX = getMetricName("zkMaxLatency");
    public static final String METRIC_ZK_CONNECTIONS = getMetricName("zkConnections");
    public static final String METRIC_ZK_OUTSTANDING_REQUESTS = getMetricName("zkOutstandingRequests");

    private static final int CONNECTION_TIMEOUT_MS = 500;
    private static final int WRITE_TIMEOUT_MS = 250;
    private static final int READ_TIMEOUT_MS = 500;

    private AtomicReference<Map<String, Long>> zkMetrics = new AtomicReference<>(new HashMap<>());
    private final ScheduledExecutorService executorService;
    private final int zkPort;

    public ZKMetricUpdater(ZookeeperServerConfig zkServerConfig, long delayMS, long intervalMS) {
        this.zkPort = zkServerConfig.clientPort();
        if (intervalMS <= 0 ) throw new IllegalArgumentException("interval must be positive, was " + intervalMS + " ms");
        this.executorService = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("zkmetricupdater"));
        this.executorService.scheduleAtFixedRate(this, delayMS, intervalMS, TimeUnit.MILLISECONDS);
    }

    private void setMetricAttribute(String attribute, long value, Map<String, Long> data) {
        switch (attribute) {
        case "zk_znode_count":
            data.put(METRIC_ZK_ZNODES, value);
            break;
        case "zk_avg_latency":
            data.put(METRIC_ZK_LATENCY_AVERAGE, value);
            break;
        case "zk_max_latency":
            data.put(METRIC_ZK_LATENCY_MAX, value);
            break;
        case "zk_num_alive_connections":
            data.put(METRIC_ZK_CONNECTIONS, value);
            break;
        case "zk_outstanding_requests":
            data.put(METRIC_ZK_OUTSTANDING_REQUESTS, value);
            break;
        }
    }

    @Override
    public void run() {
        Optional<String> report = retrieveReport();
        report.ifPresent(this::parseReport);
    }

    public void shutdown() {
        executorService.shutdown();
    }

    private Optional<String> retrieveReport() {
        try (AsynchronousSocketChannel chan = AsynchronousSocketChannel.open()) {
            InetSocketAddress zkAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), zkPort);
            Future<Void> connected = chan.connect(zkAddress);
            connected.get(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            Future<Integer> written = chan.write(ByteBuffer.wrap("mntr\n".getBytes(StandardCharsets.UTF_8)));
            written.get(WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            int nread = -1;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            do {
                Future<Integer> read = chan.read(buffer);
                nread = read.get(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                buffer.flip();
                baos.write(buffer.array());
                buffer.clear();
            } while (nread >= 0);

            return Optional.of(baos.toString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            log.warning("Failure in retrieving monitoring data: (" + e.getClass().getName() + ") " + e.getMessage());
            return Optional.empty();
        }
    }

    private static final Pattern MONITORING_REPORT = Pattern.compile("^(\\w+)\\s+(\\d+)$", Pattern.MULTILINE);

    private void parseReport(String report) {
        Matcher matcher = MONITORING_REPORT.matcher(report);
        Map<String, Long> data = new HashMap<>();
        while (matcher.find()) {
            String attribute = matcher.group(1);
            long value = Long.parseLong(matcher.group(2));
            setMetricAttribute(attribute, value, data);
        }
        zkMetrics.set(data);
    }

    public Map<String, Long> getZKMetrics() {
        return zkMetrics.get();
    }
}
