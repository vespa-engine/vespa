// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author oyving
 */
public class MetricsReporter extends Maintainer {

    private final Metric metric;

    public MetricsReporter(NodeRepository nodeRepository, Metric metric, Duration interval, JobControl jobControl) {
        super(nodeRepository, interval, jobControl);
        this.metric = metric;
    }

    @Override
    public void maintain() {
        for (Node.State state : Node.State.values())
            metric.set("hostedVespa." + state.name() + "Hosts", 
                       nodeRepository().getNodes(NodeType.tenant, state).size(), null);
    }

    @Override
    public String toString() { return "Metrics reporter"; }
    
}
