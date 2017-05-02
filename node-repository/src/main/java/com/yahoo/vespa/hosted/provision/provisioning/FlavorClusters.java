// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps track of flavor clusters: disjoint set of flavors that are connected through 'replaces'.
 * Given a node n, which has the flavor x, the flavor cluster of x is the set of flavors that
 * n could get next time it is redeployed.
 *
 * @author freva
 */
public class FlavorClusters {
    final Set<Set<Flavor>> flavorClusters;

    public FlavorClusters(List<Flavor> flavors) {
        // Make each flavor and its immediate replacements own cluster
        Set<Set<Flavor>> prevClusters = flavors.stream()
                .map(flavor -> {
                    Set<Flavor> cluster = new HashSet<>(flavor.replaces());
                    cluster.add(flavor);
                    return cluster;
                }).collect(Collectors.toSet());

        // See if any clusters intersect, if so merge them. Repeat until all the clusters are disjoint.
        while (true) {
            Set<Set<Flavor>> newClusters = new HashSet<>();
            for (Set<Flavor> oldCluster : prevClusters) {
                Optional<Set<Flavor>> overlappingCluster = newClusters.stream()
                        .filter(cluster -> !Collections.disjoint(cluster, oldCluster))
                        .findFirst();

                if (overlappingCluster.isPresent()) {
                    overlappingCluster.get().addAll(oldCluster);
                } else {
                    newClusters.add(oldCluster);
                }
            }

            if (prevClusters.size() == newClusters.size()) break;
            prevClusters = newClusters;
        }

        flavorClusters = prevClusters;
    }

    public Set<Flavor> getFlavorClusterFor(Flavor flavor) {
        return flavorClusters.stream()
                .filter(cluster -> cluster.contains(flavor))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find cluster for flavor " + flavor));
    }
}
