// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jdisc.Metric;
import com.yahoo.lang.MutableInteger;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsFetcher;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsDb;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsResponse;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.Set;
import java.util.logging.Level;

/**
 * Maintainer which keeps the node metric db up to date by periodically fetching metrics from all active nodes.
 *
 * @author bratseth
 */
public class NodeMetricsDbMaintainer extends NodeRepositoryMaintainer {

    private static final int maxWarningsPerInvocation = 2;

    private final MetricsFetcher metricsFetcher;
    private final MetricsDb metricsDb;

    public NodeMetricsDbMaintainer(NodeRepository nodeRepository,
                                   MetricsFetcher metricsFetcher,
                                   MetricsDb metricsDb,
                                   Duration interval,
                                   Metric metric) {
        super(nodeRepository, interval, metric);
        this.metricsFetcher = metricsFetcher;
        this.metricsDb = metricsDb;
    }

    @Override
    protected boolean maintain() {
        try {
            var warnings = new MutableInteger(0);
            Set<ApplicationId> applications = activeNodesByApplication().keySet();
            if (applications.isEmpty()) return true;

            long pauseMs = interval().toMillis() / applications.size() - 1; // spread requests over interval
            int done = 0;
            for (ApplicationId application : applications) {
                metricsFetcher.fetchMetrics(application)
                              .whenComplete((metricsResponse, exception) -> handleResponse(metricsResponse,
                                                                                           exception,
                                                                                           warnings,
                                                                                           application));
                if (++done < applications.size())
                    Thread.sleep(pauseMs);
            }
            metricsDb.gc();

            // Suppress failures for manual zones for now to avoid noise
            return nodeRepository().zone().environment().isManuallyDeployed() || warnings.get() == 0;
        }
        catch (InterruptedException e) {
            return false;
        }
    }

    private void handleResponse(MetricsResponse response,
                                Throwable exception,
                                MutableInteger warnings,
                                ApplicationId application) {
        if (exception != null) {
            if (warnings.get() < maxWarningsPerInvocation)
                log.log(Level.WARNING, "Could not update metrics for " + application + ": " +
                                       Exceptions.toMessageString(exception));
            warnings.add(1);
        }
        else if (response != null) {
            metricsDb.addNodeMetrics(response.nodeMetrics());
            metricsDb.addClusterMetrics(application, response.clusterMetrics());
        }
    }

}
