// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.clustercontroller;

import com.yahoo.config.model.api.Reindexing;
import com.yahoo.documentmodel.NewDocumentType;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Context required to configure automatic reindexing for a given cluster controller cluster (for a given content cluster).
 *
 * @author bjorncs
 */
public class ReindexingContext {

    private final Object monitor = new Object();
    private final Map<String, Set<NewDocumentType>> documentTypesPerCluster = new HashMap<>();
    private final Reindexing reindexing;

    public ReindexingContext(Reindexing reindexing) {
        this.reindexing = Objects.requireNonNull(reindexing);
    }

    public void addDocumentType(String clusterId, NewDocumentType type) {
        synchronized (monitor) {
            documentTypesPerCluster.computeIfAbsent(clusterId, ignored -> new HashSet<>())
                    .add(type);
        }
    }

    public Collection<String> clusterIds() {
        synchronized (monitor) {
            return new HashSet<>(documentTypesPerCluster.keySet());
        }
    }

    public Collection<NewDocumentType> documentTypesForCluster(String clusterId) {
        synchronized (monitor) {
            return new HashSet<>(documentTypesPerCluster.getOrDefault(clusterId, Set.of()));
        }
    }

    public Reindexing reindexing() { return reindexing; }

}
