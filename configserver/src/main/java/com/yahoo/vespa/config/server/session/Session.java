// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.component.Version;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.tenant.TenantRepository;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A session represents an instance of an application that can be edited, prepared and activated.
 *
 * @author Ulf Lilleengen
 * @author hmusum
 */
public class Session implements Comparable<Session>  {

    protected final long sessionId;
    protected final TenantName tenant;
    protected final SessionZooKeeperClient sessionZooKeeperClient;
    protected final Optional<ApplicationPackage> applicationPackage;
    private final Optional<ApplicationSet> applicationSet;

    public Session(TenantName tenant, long sessionId, SessionZooKeeperClient sessionZooKeeperClient, ApplicationPackage applicationPackage) {
        this(tenant, sessionId, sessionZooKeeperClient, Optional.of(applicationPackage), Optional.empty());
    }

    public Session(TenantName tenant, long sessionId, SessionZooKeeperClient sessionZooKeeperClient,
                   Optional<ApplicationPackage> applicationPackage, Optional<ApplicationSet> applicationSet) {
        this.tenant = tenant;
        this.sessionId = sessionId;
        this.sessionZooKeeperClient = sessionZooKeeperClient;
        this.applicationPackage = applicationPackage;
        this.applicationSet = applicationSet;
    }

    public final long getSessionId() {
        return sessionId;
    }

    public Session.Status getStatus() {
        return sessionZooKeeperClient.readStatus();
    }

    public SessionZooKeeperClient getSessionZooKeeperClient() {
        return sessionZooKeeperClient;
    }

    public synchronized Session activated(ApplicationSet applicationSet) {
        Objects.requireNonNull(applicationSet, "applicationSet cannot be null");
        return new Session(tenant, sessionId, sessionZooKeeperClient, applicationPackage, Optional.of(applicationSet));
    }

    public synchronized Session deactivated() {
        return new Session(tenant, sessionId, sessionZooKeeperClient, applicationPackage, Optional.empty());
    }

    @Override
    public String toString() {
        return "Session,id=" + sessionId + ",application set=" + applicationSet + ",application package=" + applicationPackage;
    }

    public long getActiveSessionAtCreate() {
        return getMetaData().getPreviousActiveGeneration();
    }

    /**
     * The status of this session.
     */
    public enum Status {
        NEW, PREPARE, ACTIVATE, DEACTIVATE, NONE;

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
        Optional<ApplicationId> applicationId;
        // We might not be able to read application id from zookeeper
        // e.g. when the app has been deleted. Use tenant name in that case.
        try {
            applicationId = Optional.of(getApplicationId());
        } catch (Exception e) {
            applicationId = Optional.empty();
        }
        return applicationId
                .filter(appId -> ! appId.equals(ApplicationId.defaultId()))
                .map(TenantRepository::logPre)
                .orElse(TenantRepository.logPre(getTenantName()));
    }

    public Instant getCreateTime() {
        return sessionZooKeeperClient.readCreateTime();
    }

    public void setApplicationId(ApplicationId applicationId) {
        sessionZooKeeperClient.writeApplicationId(applicationId);
    }

    void setApplicationPackageReference(FileReference applicationPackageReference) {
        sessionZooKeeperClient.writeApplicationPackageReference(Optional.ofNullable(applicationPackageReference));
    }

    public void setVespaVersion(Version version) {
        sessionZooKeeperClient.writeVespaVersion(version);
    }

    public void setDockerImageRepository(Optional<DockerImage> dockerImageRepository) {
        sessionZooKeeperClient.writeDockerImageRepository(dockerImageRepository);
    }

    public void setAthenzDomain(Optional<AthenzDomain> athenzDomain) {
        sessionZooKeeperClient.writeAthenzDomain(athenzDomain);
    }

    /** Returns application id read from ZooKeeper. Will throw RuntimeException if not found */
    public ApplicationId getApplicationId() {
        return sessionZooKeeperClient.readApplicationId()
                .orElseThrow(() -> new RuntimeException("Unable to read application id for session " + sessionId));
    }

    /** Returns application id read from ZooKeeper. Will return Optional.empty() if not found */
    public Optional<ApplicationId> getOptionalApplicationId() {
        try {
            return Optional.of(getApplicationId());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public FileReference getApplicationPackageReference() {return sessionZooKeeperClient.readApplicationPackageReference(); }

    public Optional<DockerImage> getDockerImageRepository() { return sessionZooKeeperClient.readDockerImageRepository(); }

    public Version getVespaVersion() { return sessionZooKeeperClient.readVespaVersion(); }

    public Optional<AthenzDomain> getAthenzDomain() { return sessionZooKeeperClient.readAthenzDomain(); }

    public AllocatedHosts getAllocatedHosts() {
        return sessionZooKeeperClient.getAllocatedHosts();
    }

    public Transaction createDeactivateTransaction() {
        return createSetStatusTransaction(Status.DEACTIVATE);
    }

    private Transaction createSetStatusTransaction(Status status) {
        return sessionZooKeeperClient.createWriteStatusTransaction(status);
    }

    // Note: Assumes monotonically increasing session ids
    public boolean isNewerThan(long sessionId) { return getSessionId() > sessionId; }

    public ApplicationMetaData getMetaData() {
        return applicationPackage.isPresent()
                ? applicationPackage.get().getMetaData()
                : sessionZooKeeperClient.loadApplicationPackage().getMetaData();
    }

    public ApplicationPackage getApplicationPackage() {
        return applicationPackage.orElseThrow(() -> new RuntimeException("No application package found for " + this));
    }

    public ApplicationFile getApplicationFile(Path relativePath, Session.Mode mode) {
        if (mode.equals(Session.Mode.WRITE)) {
            markSessionEdited();
        }
        return getApplicationPackage().getFile(relativePath);
    }

    Optional<ApplicationSet> applicationSet() { return applicationSet; }

    private void markSessionEdited() {
        setStatus(Session.Status.NEW);
    }

    void setStatus(Session.Status newStatus) {
        sessionZooKeeperClient.writeStatus(newStatus);
    }

    @Override
    public int compareTo(Session rhs) {
        Long lhsId = getSessionId();
        Long rhsId = rhs.getSessionId();
        return lhsId.compareTo(rhsId);
    }

    public enum Mode {
        READ, WRITE
    }

}
