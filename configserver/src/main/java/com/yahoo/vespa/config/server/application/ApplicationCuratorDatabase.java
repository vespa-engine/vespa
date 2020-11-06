// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.text.Utf8;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.application.ApplicationReindexing.Cluster;
import com.yahoo.vespa.config.server.application.ApplicationReindexing.Status;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Stores data and holds locks for the applications of a tenant, backed by a {@link Curator}.
 *
 * Each application is stored under /config/v2/tenants/&lt;tenant&gt;/applications/&lt;application&gt;,
 * the root contains the currently active session, if any. Children of this node may hold more data.
 * Locks for synchronising writes to these paths, and changes to the config of this application, are found
 * under /config/v2/tenants/&lt;tenant&gt;/locks/&lt;application&gt;.
 *
 * @author jonmv
 */
public class ApplicationCuratorDatabase {

    final TenantName tenant;
    final Path applicationsPath;
    final Path locksPath;

    private final Curator curator;

    public ApplicationCuratorDatabase(TenantName tenant, Curator curator) {
        this.tenant = tenant;
        this.applicationsPath = TenantRepository.getApplicationsPath(tenant);
        this.locksPath = TenantRepository.getLocksPath(tenant);
        this.curator = curator;
    }

    /** Returns the lock for changing the session status of the given application. */
    public Lock lock(ApplicationId id) {
        return curator.lock(lockPath(id), Duration.ofMinutes(1)); // These locks shouldn't be held for very long.
    }

    public boolean exists(ApplicationId id) {
        return curator.exists(applicationPath(id));
    }

    /**
     * Creates a node for the given application, marking its existence.
     */
    public void createApplication(ApplicationId id) {
        if ( ! id.tenant().equals(tenant))
            throw new IllegalArgumentException("Cannot write application id '" + id + "' for tenant '" + tenant + "'");
        try (Lock lock = lock(id)) {
            curator.create(applicationPath(id));
        }
    }

    /**
     * Returns a transaction which writes the given session id as the currently active for the given application.
     *
     * @param applicationId An {@link ApplicationId} that represents an active application.
     * @param sessionId Id of the session containing the application package for this id.
     */
    public Transaction createPutTransaction(ApplicationId applicationId, long sessionId) {
        return new CuratorTransaction(curator).add(CuratorOperations.setData(applicationPath(applicationId).getAbsolute(), Utf8.toAsciiBytes(sessionId)));
    }

    /**
     * Returns a transaction which deletes this application.
     */
    public CuratorTransaction createDeleteTransaction(ApplicationId applicationId) {
        return CuratorTransaction.from(CuratorOperations.deleteAll(applicationPath(applicationId).getAbsolute(), curator), curator);
    }

    /**
     * Returns the active session id for the given application.
     * Returns Optional.empty if application not found or no active session exists.
     */
    public Optional<Long> activeSessionOf(ApplicationId id) {
        Optional<byte[]> data = curator.getData(applicationPath(id));
        return (data.isEmpty() || data.get().length == 0)
               ? Optional.empty()
               : data.map(bytes -> Long.parseLong(Utf8.toString(bytes)));
    }

    /**
     * List the active applications of a tenant in this config server.
     *
     * @return a list of {@link ApplicationId}s that are active.
     */
    public List<ApplicationId> activeApplications() {
        return curator.getChildren(applicationsPath).stream()
                      .sorted()
                      .map(ApplicationId::fromSerializedForm)
                      .filter(id -> activeSessionOf(id).isPresent())
                      .collect(Collectors.toUnmodifiableList());
    }

    public Optional<ApplicationReindexing> readReindexingStatus(ApplicationId id) {
        return curator.getData(reindexingDataPath(id))
                      .map(data -> ReindexingStatusSerializer.fromBytes(data));
    }

    public void writeReindexingStatus(ApplicationId id, ApplicationReindexing status) {
        curator.set(reindexingDataPath(id), ReindexingStatusSerializer.toBytes(status));
    }


    /** Sets up a listenable cache with the given listener, over the applications path of this tenant. */
    public Curator.DirectoryCache createApplicationsPathCache(ExecutorService zkCacheExecutor) {
        return curator.createDirectoryCache(applicationsPath.getAbsolute(), false, false, zkCacheExecutor);
    }


    private Path lockPath(ApplicationId id) {
        return locksPath.append(id.serializedForm());
    }

    private Path applicationPath(ApplicationId id) {
        return applicationsPath.append(id.serializedForm());
    }

    private Path reindexingDataPath(ApplicationId id) {
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
