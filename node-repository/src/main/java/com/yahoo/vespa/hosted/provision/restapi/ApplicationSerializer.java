// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.ScalingEvent;
import com.yahoo.vespa.hosted.provision.autoscale.ClusterNodesTimeseries;
import com.yahoo.vespa.hosted.provision.autoscale.ClusterTimeseries;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsDb;
import com.yahoo.vespa.hosted.provision.autoscale.Resource;
import com.yahoo.vespa.hosted.provision.autoscale.ResourceTarget;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * Serializes application information for nodes/v2/application responses
 *
 * @author bratseth
 */
public class ApplicationSerializer {

    public static Slime toSlime(Application application, NodeList applicationNodes, MetricsDb metricsDb, URI applicationUri) {
        Slime slime = new Slime();
        toSlime(application, applicationNodes, metricsDb, slime.setObject(), applicationUri);
        return slime;
    }

    private static void toSlime(Application application,
                                NodeList applicationNodes,
                                MetricsDb metricsDb,
                                Cursor object,
                                URI applicationUri) {
        object.setString("url", applicationUri.toString());
        object.setString("id", application.id().toFullString());
        clustersToSlime(application, applicationNodes, metricsDb, object.setObject("clusters"));
    }

    private static void clustersToSlime(Application application,
                                        NodeList applicationNodes,
                                        MetricsDb metricsDb,
                                        Cursor clustersObject) {
        application.clusters().values().forEach(cluster -> toSlime(application, cluster, applicationNodes, metricsDb, clustersObject));
    }

    private static void toSlime(Application application,
                                Cluster cluster,
                                NodeList applicationNodes,
                                MetricsDb metricsDb,
                                Cursor clustersObject) {
        NodeList nodes = applicationNodes.not().retired().cluster(cluster.id());
        if (nodes.isEmpty()) return;
        ClusterResources currentResources = nodes.toResources();
        Duration scalingDuration = cluster.scalingDuration(nodes.clusterSpec());
        var clusterNodesTimeseries = new ClusterNodesTimeseries(Duration.ofHours(1), cluster, nodes, metricsDb);
        var clusterTimeseries = metricsDb.getClusterTimeseries(application.id(), cluster.id());

        Cursor clusterObject = clustersObject.setObject(cluster.id().value());
        clusterObject.setString("type", nodes.clusterSpec().type().name());
        toSlime(cluster.minResources(), clusterObject.setObject("min"));
        toSlime(cluster.maxResources(), clusterObject.setObject("max"));
        toSlime(currentResources, clusterObject.setObject("current"));
        if (cluster.shouldSuggestResources(currentResources))
            cluster.suggestedResources().ifPresent(suggested -> toSlime(suggested.resources(), clusterObject.setObject("suggested")));
        cluster.targetResources().ifPresent(target -> toSlime(target, clusterObject.setObject("target")));
        clusterUtilizationToSlime(application, scalingDuration, clusterTimeseries, clusterNodesTimeseries, clusterObject.setObject("utilization"));
        scalingEventsToSlime(cluster.scalingEvents(), clusterObject.setArray("scalingEvents"));
        clusterObject.setString("autoscalingStatus", cluster.autoscalingStatus());
        clusterObject.setLong("scalingDuration", scalingDuration.toMillis());
        clusterObject.setDouble("maxQueryGrowthRate", clusterTimeseries.maxQueryGrowthRate());
        clusterObject.setDouble("currentQueryFractionOfMax", clusterTimeseries.currentQueryFractionOfMax());
    }

    private static void toSlime(ClusterResources resources, Cursor clusterResourcesObject) {
        clusterResourcesObject.setLong("nodes", resources.nodes());
        clusterResourcesObject.setLong("groups", resources.groups());
        NodeResourcesSerializer.toSlime(resources.nodeResources(), clusterResourcesObject.setObject("resources"));
    }

    private static void clusterUtilizationToSlime(Application application,
                                                  Duration scalingDuration,
                                                  ClusterTimeseries clusterTimeseries,
                                                  ClusterNodesTimeseries clusterNodesTimeseries,
                                                  Cursor utilizationObject) {
        utilizationObject.setDouble("cpu", clusterNodesTimeseries.averageLoad(Resource.cpu));
        utilizationObject.setDouble("idealCpu", ResourceTarget.idealCpuLoad(scalingDuration, clusterTimeseries, application));
        utilizationObject.setDouble("memory", clusterNodesTimeseries.averageLoad(Resource.memory));
        utilizationObject.setDouble("idealMemory", ResourceTarget.idealMemoryLoad());
        utilizationObject.setDouble("disk", clusterNodesTimeseries.averageLoad(Resource.disk));
        utilizationObject.setDouble("idealDisk", ResourceTarget.idealDiskLoad());
    }

    private static void scalingEventsToSlime(List<ScalingEvent> scalingEvents, Cursor scalingEventsArray) {
        for (ScalingEvent scalingEvent : scalingEvents) {
            Cursor scalingEventObject = scalingEventsArray.addObject();
            toSlime(scalingEvent.from(), scalingEventObject.setObject("from"));
            toSlime(scalingEvent.to(), scalingEventObject.setObject("to"));
            scalingEventObject.setLong("at", scalingEvent.at().toEpochMilli());
        }
    }

}
