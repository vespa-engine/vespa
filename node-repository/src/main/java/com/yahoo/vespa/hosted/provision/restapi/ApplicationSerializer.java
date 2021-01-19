// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.ScalingEvent;
import com.yahoo.vespa.hosted.provision.autoscale.AllocatableClusterResources;

import java.net.URI;
import java.util.Collection;
import java.util.List;

/**
 * Serializes application information for nodes/v2/application responses
 */
public class ApplicationSerializer {

    public static Slime toSlime(Application application, List<Node> applicationNodes, URI applicationUri) {
        Slime slime = new Slime();
        toSlime(application, applicationNodes, slime.setObject(), applicationUri);
        return slime;
    }

    private static void toSlime(Application application,
                                List<Node> applicationNodes,
                                Cursor object,
                                URI applicationUri) {
        object.setString("url", applicationUri.toString());
        object.setString("id", application.id().toFullString());
        clustersToSlime(application.clusters().values(), applicationNodes, object.setObject("clusters"));
    }

    private static void clustersToSlime(Collection<Cluster> clusters, List<Node> applicationNodes, Cursor clustersObject) {
        clusters.forEach(cluster -> toSlime(cluster, applicationNodes, clustersObject));
    }

    private static void toSlime(Cluster cluster, List<Node> applicationNodes, Cursor clustersObject) {
        NodeList nodes = NodeList.copyOf(applicationNodes).not().retired().cluster(cluster.id());
        if (nodes.isEmpty()) return;
        ClusterResources currentResources = nodes.toResources();

        Cursor clusterObject = clustersObject.setObject(cluster.id().value());
        toSlime(cluster.minResources(), clusterObject.setObject("min"));
        toSlime(cluster.maxResources(), clusterObject.setObject("max"));
        toSlime(currentResources, clusterObject.setObject("current"));
        if (cluster.shouldSuggestResources(currentResources))
            cluster.suggestedResources().ifPresent(suggested -> toSlime(suggested.resources(), clusterObject.setObject("suggested")));
        cluster.targetResources().ifPresent(target -> toSlime(target, clusterObject.setObject("target")));
        scalingEventsToSlime(cluster.scalingEvents(), clusterObject.setArray("scalingEvents"));
        clusterObject.setString("autoscalingStatus", cluster.autoscalingStatus());
    }

    private static void toSlime(ClusterResources resources, Cursor clusterResourcesObject) {
        clusterResourcesObject.setLong("nodes", resources.nodes());
        clusterResourcesObject.setLong("groups", resources.groups());
        NodeResourcesSerializer.toSlime(resources.nodeResources(), clusterResourcesObject.setObject("resources"));
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
