// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.monitoring;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.net.HostName;
import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
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

    private static final int CONNECTION_TIMEOUT_MS = 1000;
    private static final int READ_TIMEOUT_MS = 1000;

    private final AtomicReference<Map<String, Long>> zkMetrics = new AtomicReference<>(new HashMap<>());
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
        try {
            Socket socket = null;
            InputStream in = null;
            OutputStream out = null;
            try {
                socket = createSocket();
                socket.setSoTimeout(READ_TIMEOUT_MS);
                socket.connect(new InetSocketAddress(HostName.getLocalhost(), zkPort), CONNECTION_TIMEOUT_MS);
                in = socket.getInputStream();
                out = socket.getOutputStream();
                out.write("mntr\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } finally {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && socket.isConnected()) socket.close();
            }
        } catch (Exception e) {
            log.warning("Failure in retrieving monitoring data: (" + e.getClass().getSimpleName() + ") " + e.getMessage());
            log.log(Level.FINE, e, e::toString);
            return Optional.empty();
        }
    }

    private static Socket createSocket() throws IOException {
        TlsContext tlsContext = TransportSecurityUtils.getSystemTlsContext().orElse(null);
        if (tlsContext == null || TransportSecurityUtils.getInsecureMixedMode() == MixedMode.PLAINTEXT_CLIENT_MIXED_SERVER) {
            return new Socket();
        }
        return tlsContext.context().getSocketFactory().createSocket();
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
