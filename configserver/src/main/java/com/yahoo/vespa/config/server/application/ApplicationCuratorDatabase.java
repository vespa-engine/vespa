// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.config.server.application.ApplicationReindexing.Cluster;
import com.yahoo.vespa.config.server.application.ApplicationReindexing.Status;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.yolean.Exceptions;

import java.time.Instant;

import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Stores data and holds locks for application, backed by a {@link Curator}.
 *
 * @author jonmv
 */
public class ApplicationCuratorDatabase {

    private final Curator curator;

    public ApplicationCuratorDatabase(Curator curator) {
        this.curator = curator;
    }

    public ApplicationReindexing readReindexingStatus(ApplicationId id) {
        return curator.getData(reindexingDataPath(id))
                      .map(data -> ReindexingStatusSerializer.fromBytes(data))
                      .orElse(ApplicationReindexing.empty());
    }

    public void writeReindexingStatus(ApplicationId id, ApplicationReindexing status) {
        curator.set(reindexingDataPath(id), ReindexingStatusSerializer.toBytes(status));
    }

    private static Path applicationsRoot(TenantName tenant) {
        return TenantRepository.getApplicationsPath(tenant);
    }

    private static Path applicationPath(ApplicationId id) {
        return applicationsRoot(id.tenant()).append(id.serializedForm());
    }

    private static Path reindexingDataPath(ApplicationId id) {
        return applicationPath(id).append("reindexing");
    }


    private static class ReindexingStatusSerializer {

        private static final String COMMON = "common";
        private static final String CLUSTERS = "clusters";
        private static final String PENDING = "pending";
        private static final String READY = "ready";
        private static final String TYPE = "type";
        private static final String NAME = "name";
        private static final String GENERATION = "generation";
        private static final String EPOCH_MILLIS = "epochMillis";

        private static byte[] toBytes(ApplicationReindexing reindexing) {
            Cursor root = new Slime().setObject();
            setStatus(root.setObject(COMMON), reindexing.common());

            Cursor clustersArray = root.setArray(CLUSTERS);
            reindexing.clusters().forEach((name, cluster) -> {
                Cursor clusterObject = clustersArray.addObject();
                clusterObject.setString(NAME, name);
                setStatus(clusterObject.setObject(COMMON), cluster.common());

                Cursor pendingArray = clusterObject.setArray(PENDING);
                cluster.pending().forEach((type, generation) -> {
                    Cursor pendingObject =  pendingArray.addObject();
                    pendingObject.setString(TYPE, type);
                    pendingObject.setLong(GENERATION, generation);
                });

                Cursor readyArray = clusterObject.setArray(READY);
                cluster.ready().forEach((type, status) -> {
                    Cursor statusObject = readyArray.addObject();
                    statusObject.setString(TYPE, type);
                    setStatus(statusObject, status);
                });
            });
            return Exceptions.uncheck(() -> SlimeUtils.toJsonBytes(root));
        }

        private static void setStatus(Cursor statusObject, Status status) {
            statusObject.setLong(EPOCH_MILLIS, status.ready().toEpochMilli());
        }

        private static ApplicationReindexing fromBytes(byte[] data) {
            Cursor root = SlimeUtils.jsonToSlimeOrThrow(data).get();
            return new ApplicationReindexing(getStatus(root.field(COMMON)),
                                             SlimeUtils.entriesStream(root.field(CLUSTERS))
                                                       .collect(toUnmodifiableMap(object -> object.field(NAME).asString(),
                                                                                  object -> getCluster(object))));
        }

        private static Cluster getCluster(Inspector object) {
            return new Cluster(getStatus(object.field(COMMON)),
                               SlimeUtils.entriesStream(object.field(PENDING))
                                         .collect(toUnmodifiableMap(entry -> entry.field(TYPE).asString(),
                                                                    entry -> entry.field(GENERATION).asLong())),
                               SlimeUtils.entriesStream(object.field(READY))
                                         .collect(toUnmodifiableMap(entry -> entry.field(TYPE).asString(),
                                                                    entry -> getStatus(entry))));
        }

        private static Status getStatus(Inspector statusObject) {
            return new Status(Instant.ofEpochMilli(statusObject.field(EPOCH_MILLIS).asLong()));
        }

    }

}
