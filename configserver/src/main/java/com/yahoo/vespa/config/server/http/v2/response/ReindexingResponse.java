// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.response;

import com.yahoo.jdisc.Response;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.server.application.ApplicationReindexing;
import com.yahoo.vespa.config.server.application.ClusterReindexing;
import com.yahoo.vespa.config.server.http.JSONResponse;

import java.util.Map;
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
                if (reindexing.clusters().containsKey(cluster)) {
                    if (reindexing.clusters().get(cluster).pending().containsKey(type))
                        pendingObject.setLong(type, reindexing.clusters().get(cluster).pending().get(type));

                    if (reindexing.clusters().get(cluster).ready().containsKey(type))
                        setStatus(statusObject, reindexing.clusters().get(cluster).ready().get(type));
                }
                if (clusters.containsKey(cluster))
                    if (clusters.get(cluster).documentTypeStatus().containsKey(type))
                        setStatus(statusObject, clusters.get(cluster).documentTypeStatus().get(type));
            }
        });
    }

    private static void setStatus(Cursor object, ApplicationReindexing.Status readyStatus) {
        object.setLong("readyMillis", readyStatus.ready().toEpochMilli());
        object.setDouble("speed", readyStatus.speed());
    }

    private static void setStatus(Cursor object, ClusterReindexing.Status status) {
        object.setLong("startedMillis", status.startedAt().toEpochMilli());
        status.endedAt().ifPresent(endedAt -> object.setLong("endedMillis", endedAt.toEpochMilli()));
        status.state().map(ClusterReindexing.State::asString).ifPresent(state -> object.setString("state", state));
        status.message().ifPresent(message -> object.setString("message", message));
        status.progress().ifPresent(progress -> object.setDouble("progress", progress));
    }

}
