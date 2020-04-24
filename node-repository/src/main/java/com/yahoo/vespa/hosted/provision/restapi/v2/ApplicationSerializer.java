// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

import java.net.URI;
import java.util.Collection;

/**
 * Serializes application information for nodes/v2/application responses
 */
public class ApplicationSerializer {

    public static Slime toSlime(Application application, URI applicationUri) {
        Slime slime = new Slime();
        toSlime(application, slime.setObject(), applicationUri);
        return slime;
    }

    private static void toSlime(Application application, Cursor object, URI applicationUri) {
        object.setString("url", applicationUri.toString());
        object.setString("id", application.id().toFullString());
        clustersToSlime(application.clusters().values(), object.setObject("clusters"));
    }

    private static void clustersToSlime(Collection<Cluster> clusters, Cursor clustersObject) {
        clusters.forEach(cluster -> toSlime(cluster, clustersObject.setObject(cluster.id().value())));
    }

    private static void toSlime(Cluster cluster, Cursor clusterObject) {
        toSlime(cluster.minResources(), clusterObject.setObject("min"));
        toSlime(cluster.maxResources(), clusterObject.setObject("max"));
        cluster.suggestedResources().ifPresent(suggested -> toSlime(suggested, clusterObject.setObject("suggested")));
        cluster.targetResources().ifPresent(target -> toSlime(target, clusterObject.setObject("target")));
    }

    private static void toSlime(ClusterResources resources, Cursor clusterResourcesObject) {
        clusterResourcesObject.setLong("nodes", resources.nodes());
        clusterResourcesObject.setLong("groups", resources.groups());
        NodeResourcesSerializer.toSlime(resources.nodeResources(), clusterResourcesObject.setObject("resources"));
    }

}
