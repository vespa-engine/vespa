// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.jrt;

import com.google.inject.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.TransportMetrics;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;

/**
 * Propagates JRT metric values ({@link TransportMetrics} to {@link Metric}.
 *
 * @author bjorncs
 */
public class JrtMetricsUpdater {

    private final Object monitor = new Object();
    private final Timer timer = new Timer("jrt-metrics-updater", true);
    private final Map<TransportMetrics, Metric.Context> transportMetricsInstances = new WeakHashMap<>();
    private final Metric metric;

    @Inject
    public JrtMetricsUpdater(Metric metric) {
        this.metric = metric;
        timer.scheduleAtFixedRate(new UpdaterTask(), /*delay ms*/0, /*period ms*/10_000);
    }

    public void register(Supervisor supervisor) {
        register(supervisor, null);
    }

    public void register(Supervisor supervisor, Map<String, ?> dimensions) {
        synchronized (monitor) {
            this.transportMetricsInstances.put(supervisor.transport().metrics(), metric.createContext(dimensions));
        }
    }

    public void deregister(Supervisor supervisor) {
        synchronized (monitor) {
            this.transportMetricsInstances.remove(supervisor.transport().metrics());
        }
    }

    public void stop() {
        timer.cancel();
        synchronized (monitor) {
            this.transportMetricsInstances.clear();
        }
    }

    private class UpdaterTask extends TimerTask {
        @Override
        public void run() {
            synchronized (monitor) {
                transportMetricsInstances.forEach((instance, context) -> {
                    metric.add("jrt.transport.tls-certificate-verification-failures", instance.tlsCertificateVerificationFailures(), context);
                    metric.add("jrt.transport.peer-authorization-failures", instance.peerAuthorizationFailures(), context);
                    metric.add("jrt.transport.server.tls-connections-established", instance.serverTlsConnectionsEstablished(), context);
                    metric.add("jrt.transport.client.tls-connections-established", instance.clientTlsConnectionsEstablished(), context);
                    metric.add("jrt.transport.server.unencrypted-connections-established", instance.serverUnencryptedConnectionsEstablished(), context);
                    metric.add("jrt.transport.client.unencrypted-connections-established", instance.clientUnencryptedConnectionsEstablished(), context);
                });
            }
        }
    }
}
