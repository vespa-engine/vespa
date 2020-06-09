// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.component.Version;
import com.yahoo.config.FileReference;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.tenant.TenantRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * A session represents an instance of an application that can be edited, prepared and activated. This
 * class represents the common stuff between sessions working on the local file
 * system ({@link LocalSession}s) and sessions working on zookeeper {@link RemoteSession}s.
 *
 * @author Ulf Lilleengen
 */
public abstract class Session implements Comparable<Session>  {

    private final long sessionId;
    protected final TenantName tenant;
    protected final SessionZooKeeperClient zooKeeperClient;

    protected Session(TenantName tenant, long sessionId, SessionZooKeeperClient zooKeeperClient) {
        this.tenant = tenant;
        this.sessionId = sessionId;
        this.zooKeeperClient = zooKeeperClient;
    }
    /**
     * Retrieve the session id for this session.
     * @return the session id.
     */
    public final long getSessionId() {
        return sessionId;
    }

    public Session.Status getStatus() {
        return zooKeeperClient.readStatus();
    }

    @Override
    public String toString() {
        return "Session,id=" + sessionId;
    }

    /**
     * Represents the status of this session.
     */
    public enum Status {
        NEW, PREPARE, ACTIVATE, DEACTIVATE, DELETE, NONE;

        public static Status parse(String data) {
            for (Status status : Status.values()) {
                if (status.name().equals(data)) {
                    return status;
                }
            }
            return Status.NEW;
        }
    }

    public TenantName getTenantName() { return tenant; }

    /**
     * Helper to provide a log message preamble for code dealing with sessions
     * @return log preamble
     */
    public String logPre() {
        if (getApplicationId().equals(ApplicationId.defaultId())) {
            return TenantRepository.logPre(getTenantName());
        } else {
            return TenantRepository.logPre(getApplicationId());
        }
    }

    public Instant getCreateTime() {
        return zooKeeperClient.readCreateTime();
    }

    public void setApplicationId(ApplicationId applicationId) {
        zooKeeperClient.writeApplicationId(applicationId);
    }

    void setApplicationPackageReference(FileReference applicationPackageReference) {
        if (applicationPackageReference == null) throw new IllegalArgumentException(String.format(
                "Null application package FileReference for tenant: %s, session: %d", tenant, sessionId));
        zooKeeperClient.writeApplicationPackageReference(applicationPackageReference);
    }

    public void setVespaVersion(Version version) {
        zooKeeperClient.writeVespaVersion(version);
    }

    public void setDockerImageRepository(Optional<DockerImage> dockerImageRepository) {
        zooKeeperClient.writeDockerImageRepository(dockerImageRepository);
    }

    public void setAthenzDomain(Optional<AthenzDomain> athenzDomain) {
        zooKeeperClient.writeAthenzDomain(athenzDomain);
    }

    public ApplicationId getApplicationId() { return zooKeeperClient.readApplicationId(); }

    public FileReference getApplicationPackageReference() {return zooKeeperClient.readApplicationPackageReference(); }

    public Optional<DockerImage> getDockerImageRepository() { return zooKeeperClient.readDockerImageRepository(); }

    public Version getVespaVersion() { return zooKeeperClient.readVespaVersion(); }

    public Optional<AthenzDomain> getAthenzDomain() { return zooKeeperClient.readAthenzDomain(); }

    public AllocatedHosts getAllocatedHosts() {
        return zooKeeperClient.getAllocatedHosts();
    }

    public Transaction createDeactivateTransaction() {
        return createSetStatusTransaction(Status.DEACTIVATE);
    }

    private Transaction createSetStatusTransaction(Status status) {
        return zooKeeperClient.createWriteStatusTransaction(status);
    }

    // Note: Assumes monotonically increasing session ids
    public boolean isNewerThan(long sessionId) { return getSessionId() > sessionId; }

    @Override
    public int compareTo(Session rhs) {
        Long lhsId = getSessionId();
        Long rhsId = rhs.getSessionId();
        return lhsId.compareTo(rhsId);
    }

}
