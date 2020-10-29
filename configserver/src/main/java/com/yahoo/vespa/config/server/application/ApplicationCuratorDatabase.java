// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.config.server.application.ReindexingStatus.Status;
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

    public ReindexingStatus readReindexingStatus(ApplicationId id) {
        return curator.getData(reindexingDataPath(id))
                      .map(data -> ReindexingStatusSerializer.fromBytes(data))
                      .orElse(ReindexingStatus.empty());
    }

    public void writeReindexingStatus(ApplicationId id, ReindexingStatus status) {
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

        private static final String PENDING = "pending";
        private static final String READY = "ready";
        private static final String TYPE = "type";
        private static final String GENERATION = "generation";
        private static final String EPOCH_MILLIS = "epochMillis";

        private static byte[] toBytes(ReindexingStatus reindexingStatus) {
            Cursor root = new Slime().setObject();
            Cursor pendingArray = root.setArray(PENDING);
            reindexingStatus.pending().forEach((type, generation) -> {
                Cursor pendingObject =  pendingArray.addObject();
                pendingObject.setString(TYPE, type);
                pendingObject.setLong(GENERATION, generation);
            });
            Cursor readyArray = root.setArray(READY);
            reindexingStatus.status().forEach((type, status) -> {
                Cursor readyObject = readyArray.addObject();
                readyObject.setString(TYPE, type);
                readyObject.setLong(EPOCH_MILLIS, status.ready().toEpochMilli());
            });
            return Exceptions.uncheck(() -> SlimeUtils.toJsonBytes(root));
        }

        private static ReindexingStatus fromBytes(byte[] data) {
            Cursor root = SlimeUtils.jsonToSlimeOrThrow(data).get();
            return new ReindexingStatus(SlimeUtils.entriesStream(root.field(PENDING))
                                                  .filter(entry -> entry.field(TYPE).valid() && entry.field(GENERATION).valid())
                                                  .collect(toUnmodifiableMap(entry -> entry.field(TYPE).asString(),
                                                                             entry -> entry.field(GENERATION).asLong())),
                                        SlimeUtils.entriesStream(root.field(READY))
                                                  .filter(entry -> entry.field(TYPE).valid() && entry.field(EPOCH_MILLIS).valid())
                                                  .collect(toUnmodifiableMap(entry -> entry.field(TYPE).asString(),
                                                                             entry -> new Status(Instant.ofEpochMilli(entry.field(EPOCH_MILLIS).asLong())))));
        }

    }

}
