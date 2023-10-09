// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.response;

import com.yahoo.jdisc.Response;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.server.application.ApplicationReindexing;
import com.yahoo.vespa.config.server.application.ClusterReindexing;
import com.yahoo.vespa.config.server.application.ClusterReindexing.State;
import com.yahoo.vespa.config.server.http.JSONResponse;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ReindexingResponse extends JSONResponse {
    public ReindexingResponse(Map<String, Set<String>> documentTypes, ApplicationReindexing reindexing,
                       Map<String, ClusterReindexing> clusters) {
        super(Response.Status.OK);
        object.setBool("enabled", reindexing.enabled());
        Cursor clustersObject = object.setObject("clusters");
        documentTypes.forEach((cluster, types) -> {
            Cursor clusterObject = clustersObject.setObject(cluster);
            Cursor pendingObject = clusterObject.setObject("pending");
            Cursor readyObject = clusterObject.setObject("ready");

            for (String type : types) {
                Cursor statusObject = readyObject.setObject(type);
                Instant readyAt = Instant.EPOCH;
                State state = null;
                if (reindexing.clusters().containsKey(cluster)) {
                    if (reindexing.clusters().get(cluster).pending().containsKey(type)) {
                        pendingObject.setLong(type, reindexing.clusters().get(cluster).pending().get(type));
                        state = State.PENDING;
                    }

                    if (reindexing.clusters().get(cluster).ready().containsKey(type)) {
                        ApplicationReindexing.Status readyStatus = reindexing.clusters().get(cluster).ready().get(type);
                        readyAt = readyStatus.ready();
                        statusObject.setLong("readyMillis", readyStatus.ready().toEpochMilli());
                        statusObject.setDouble("speed", readyStatus.speed());
                        statusObject.setString("cause", readyStatus.cause());
                    }
                }
                if (clusters.containsKey(cluster))
                    if (clusters.get(cluster).documentTypeStatus().containsKey(type)) {
                        ClusterReindexing.Status status = clusters.get(cluster).documentTypeStatus().get(type);
                        statusObject.setLong("startedMillis", status.startedAt().toEpochMilli());
                        status.endedAt().ifPresent(endedAt -> statusObject.setLong("endedMillis", endedAt.toEpochMilli()));
                        if (status.startedAt().isAfter(readyAt) && status.state().isPresent()) state = status.state().get();
                        status.message().ifPresent(message -> statusObject.setString("message", message));
                        status.progress().ifPresent(progress -> statusObject.setDouble("progress", progress));
                    }
                if (readyAt != Instant.EPOCH && state == null) state = State.PENDING;
                if (state != null) statusObject.setString("state", state.asString());
            }
        });
    }

}
