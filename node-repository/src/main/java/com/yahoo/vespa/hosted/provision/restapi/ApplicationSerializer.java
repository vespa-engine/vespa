// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.IntRange;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.ScalingEvent;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaling;
import com.yahoo.vespa.hosted.provision.autoscale.Limits;
import com.yahoo.vespa.hosted.provision.autoscale.Load;

import java.net.URI;
import java.util.List;

/**
 * Serializes application information for nodes/v2/application responses
 *
 * @author bratseth
 */
public class ApplicationSerializer {

    public static Slime toSlime(Application application,
                                NodeList applicationNodes,
                                NodeRepository nodeRepository,
                                URI applicationUri) {
        Slime slime = new Slime();
        toSlime(application, applicationNodes, nodeRepository, slime.setObject(), applicationUri);
        return slime;
    }

    private static void toSlime(Application application,
                                NodeList applicationNodes,
                                NodeRepository nodeRepository,
                                Cursor object,
                                URI applicationUri) {
        object.setString("url", applicationUri.toString());
        object.setString("id", application.id().toFullString());
        clustersToSlime(application, applicationNodes, nodeRepository, object.setObject("clusters"));
    }

    private static void clustersToSlime(Application application,
                                        NodeList applicationNodes,
                                        NodeRepository nodeRepository,
                                        Cursor clustersObject) {
        application.clusters().values().forEach(cluster -> toSlime(application, cluster, applicationNodes, nodeRepository, clustersObject));
    }

    private static void toSlime(Application application,
                                Cluster cluster,
                                NodeList applicationNodes,
                                NodeRepository nodeRepository,
                                Cursor clustersObject) {
        NodeList nodes = applicationNodes.not().retired().cluster(cluster.id());
        if (nodes.isEmpty()) return;
        ClusterResources currentResources = nodes.toResources();
        Cursor clusterObject = clustersObject.setObject(cluster.id().value());
        clusterObject.setString("type", nodes.clusterSpec().type().name());
        Limits limits = Limits.of(cluster).fullySpecified(nodes.clusterSpec(), nodeRepository, application.id());
        toSlime(limits.min(), clusterObject.setObject("min"));
        toSlime(limits.max(), clusterObject.setObject("max"));
        if ( ! cluster.groupSize().isEmpty())
            toSlime(cluster.groupSize(), clusterObject.setObject("groupSize"));
        toSlime(currentResources, clusterObject.setObject("current"));
        if (cluster.shouldSuggestResources(currentResources))
            toSlime(cluster.suggested(), clusterObject.setObject("suggested"));
        toSlime(cluster.target(), clusterObject.setObject("target"));
        scalingEventsToSlime(cluster.scalingEvents(), clusterObject.setArray("scalingEvents"));
        clusterObject.setLong("scalingDuration", cluster.scalingDuration(nodes.clusterSpec()).toMillis());
    }

    private static void toSlime(Autoscaling autoscaling, Cursor autoscalingObject) {
        autoscalingObject.setString("status", autoscaling.status().name());
        autoscalingObject.setString("description", autoscaling.description());
        autoscaling.resources().ifPresent(resources -> toSlime(resources, autoscalingObject.setObject("resources")));
        autoscalingObject.setLong("at", autoscaling.at().toEpochMilli());
        toSlime(autoscaling.peak(), autoscalingObject.setObject("peak"));
        toSlime(autoscaling.ideal(), autoscalingObject.setObject("ideal"));
        toSlime(autoscaling.metrics(), autoscalingObject.setObject("metrics"));
    }

    private static void toSlime(ClusterResources resources, Cursor clusterResourcesObject) {
        clusterResourcesObject.setLong("nodes", resources.nodes());
        clusterResourcesObject.setLong("groups", resources.groups());
        NodeResourcesSerializer.toSlime(resources.nodeResources(), clusterResourcesObject.setObject("resources"));
    }

    private static void toSlime(IntRange range, Cursor rangeObject) {
        range.from().ifPresent(from -> rangeObject.setLong("from", range.from().getAsInt()));
        range.to().ifPresent(to -> rangeObject.setLong("to", range.to().getAsInt()));
    }

    private static void toSlime(Load load, Cursor loadObject) {
        loadObject.setDouble("cpu", load.cpu());
        loadObject.setDouble("memory", load.memory());
        loadObject.setDouble("disk", load.disk());
    }

    private static void toSlime(Autoscaling.Metrics metrics, Cursor metricsObject) {
        metricsObject.setDouble("queryRate", metrics.queryRate());
        metricsObject.setDouble("growthRateHeadroom", metrics.growthRateHeadroom());
        metricsObject.setDouble("cpuCostPerQuery", metrics.cpuCostPerQuery());
    }

    private static void scalingEventsToSlime(List<ScalingEvent> scalingEvents, Cursor scalingEventsArray) {
        for (ScalingEvent scalingEvent : scalingEvents) {
            Cursor scalingEventObject = scalingEventsArray.addObject();
            toSlime(scalingEvent.from(), scalingEventObject.setObject("from"));
            toSlime(scalingEvent.to(), scalingEventObject.setObject("to"));
            scalingEventObject.setLong("at", scalingEvent.at().toEpochMilli());
            scalingEvent.completion().ifPresent(completion -> scalingEventObject.setLong("completion", completion.toEpochMilli()));
        }
    }

}
