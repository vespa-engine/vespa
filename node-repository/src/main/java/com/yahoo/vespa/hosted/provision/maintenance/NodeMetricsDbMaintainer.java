// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jdisc.Metric;
import com.yahoo.lang.MutableInteger;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsFetcher;
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

    public NodeMetricsDbMaintainer(NodeRepository nodeRepository,
                                   MetricsFetcher metricsFetcher,
                                   Duration interval,
                                   Metric metric) {
        super(nodeRepository, interval, metric);
        this.metricsFetcher = metricsFetcher;
    }

    @Override
    protected double maintain() {
        int attempts = 0;
        var failures = new MutableInteger(0);
        try {
            Set<ApplicationId> applications = activeNodesByApplication().keySet();
            if (applications.isEmpty()) return 1.0;

            long pauseMs = interval().toMillis() / applications.size() - 1; // spread requests over interval
            int done = 0;
            for (ApplicationId application : applications) {
                attempts++;
                metricsFetcher.fetchMetrics(application)
                              .whenComplete((metricsResponse, exception) -> handleResponse(metricsResponse,
                                                                                           exception,
                                                                                           failures,
                                                                                           application));
                if (++done < applications.size())
                    Thread.sleep(pauseMs);
            }

            nodeRepository().metricsDb().gc();

            return asSuccessFactorDeviation(attempts, failures.get());
        }
        catch (InterruptedException e) {
            return asSuccessFactorDeviation(attempts, failures.get());
        }
    }

    private void handleResponse(MetricsResponse response,
                                Throwable exception,
                                MutableInteger failures,
                                ApplicationId application) {
        if (exception != null) {
            if (failures.get() < maxWarningsPerInvocation)
                log.log(Level.WARNING, "Could not update metrics for " + application + ": " +
                                       Exceptions.toMessageString(exception));
            failures.add(1);
        }
        else if (response != null) {
            nodeRepository().metricsDb().addNodeMetrics(response.nodeMetrics());
            nodeRepository().metricsDb().addClusterMetrics(application, response.clusterMetrics());
        }
    }

}
