// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.monitoring;

import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author oyving
 */
public class ProvisionMetrics extends AbstractComponent {

    private static final Logger log = Logger.getLogger(ProvisionMetrics.class.getName());
    private final ScheduledExecutorService executorService;

    // TODO: make report interval configurable
    public ProvisionMetrics(Metric metric, NodeRepository nodeRepository) {
        this.executorService = new ScheduledThreadPoolExecutor(1);
        this.executorService.scheduleAtFixedRate(
                new ProvisionMetricsTask(metric, nodeRepository),
                0, // start immediately
                1, // report every minute
                TimeUnit.MINUTES
        );
    }

    @Override
    public void deconstruct() {
        this.executorService.shutdown();
    }

    private static class ProvisionMetricsTask implements Runnable {
        private final Metric metric;
        private final NodeRepository nodeRepository;

        private ProvisionMetricsTask(Metric metric, NodeRepository nodeRepository) {
            this.metric = metric;
            this.nodeRepository = nodeRepository;
        }

        @Override
        public void run() {
            log.log(LogLevel.DEBUG, "Running provision metrics task");
            try {
                for (Node.State state : Node.State.values())
                    metric.set("hostedVespa." + state.name() + "Hosts", nodeRepository.getNodes(NodeType.tenant, state).size(), null);
            } catch (RuntimeException e) {
                log.log(LogLevel.INFO, "Failed gathering metrics data: " + e.getMessage());
            }
        }
    }

}
