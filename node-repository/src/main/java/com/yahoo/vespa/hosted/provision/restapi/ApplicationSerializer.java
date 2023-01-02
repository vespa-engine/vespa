// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.ScalingEvent;
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
        toSlime(currentResources, clusterObject.setObject("current"));
        if (cluster.shouldSuggestResources(currentResources))
            cluster.suggested().resources().ifPresent(suggested -> toSlime(suggested, clusterObject.setObject("suggested")));
        cluster.target().resources().ifPresent(target -> toSlime(target, clusterObject.setObject("target")));
        clusterUtilizationToSlime(cluster.target().ideal(), cluster.target().peak(), clusterObject.setObject("utilization"));
        scalingEventsToSlime(cluster.scalingEvents(), clusterObject.setArray("scalingEvents"));
        clusterObject.setString("autoscalingStatusCode", cluster.target().status().name());
        clusterObject.setString("autoscalingStatus", cluster.target().description());
        clusterObject.setLong("scalingDuration", cluster.scalingDuration(nodes.clusterSpec()).toMillis());
    }

    private static void toSlime(ClusterResources resources, Cursor clusterResourcesObject) {
        clusterResourcesObject.setLong("nodes", resources.nodes());
        clusterResourcesObject.setLong("groups", resources.groups());
        NodeResourcesSerializer.toSlime(resources.nodeResources(), clusterResourcesObject.setObject("resources"));
    }

    private static void clusterUtilizationToSlime(Load idealLoad, Load peakLoad, Cursor utilizationObject) {
        utilizationObject.setDouble("idealCpu", idealLoad.cpu());
        utilizationObject.setDouble("peakCpu", peakLoad.cpu());

        utilizationObject.setDouble("idealMemory", idealLoad.memory());
        utilizationObject.setDouble("peakMemory", peakLoad.memory());

        utilizationObject.setDouble("idealDisk", idealLoad.disk());
        utilizationObject.setDouble("peakDisk", peakLoad.disk());
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
