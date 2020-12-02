// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.vespa.config.server.modelfactory.ModelResult;

import java.io.IOException;
import java.util.Map;

/**
 * Retrieves reindexing status from content clusters
 *
 * @author bjorncs
 */
public interface ClusterReindexingStatusClient extends AutoCloseable {

    Map<String, ClusterReindexing> getReindexingStatus(ModelResult application) throws IOException;

    void close();

    ClusterReindexingStatusClient DUMMY_INSTANCE = new ClusterReindexingStatusClient() {
        @Override public Map<String, ClusterReindexing> getReindexingStatus(ModelResult application) { return Map.of(); }
        @Override public void close() {}
    };

}
