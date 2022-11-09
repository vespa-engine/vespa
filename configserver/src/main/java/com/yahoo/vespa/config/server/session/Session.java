// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.component.Version;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Tags;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.NotFoundException;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.tenant.TenantRepository;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A session represents an instance of an application that can be edited, prepared and activated. This
 * class represents the common stuff between sessions working on the local file
 * system ({@link LocalSession}s) and sessions working on zookeeper ({@link RemoteSession}s).
 *
 * @author Ulf Lilleengen
 * @author hmusum
 */
public abstract class Session implements Comparable<Session>  {

    protected final long sessionId;
    protected final TenantName tenant;
    protected final SessionZooKeeperClient sessionZooKeeperClient;
    protected final Optional<ApplicationPackage> applicationPackage;

    protected Session(TenantName tenant, long sessionId, SessionZooKeeperClient sessionZooKeeperClient) {
        this(tenant, sessionId, sessionZooKeeperClient, Optional.empty());
    }

    protected Session(TenantName tenant, long sessionId, SessionZooKeeperClient sessionZooKeeperClient,
                      ApplicationPackage applicationPackage) {
        this(tenant, sessionId, sessionZooKeeperClient, Optional.of(applicationPackage));
    }

    private Session(TenantName tenant, long sessionId, SessionZooKeeperClient sessionZooKeeperClient,
                    Optional<ApplicationPackage> applicationPackage) {
        this.tenant = tenant;
        this.sessionId = sessionId;
        this.sessionZooKeeperClient = sessionZooKeeperClient;
        this.applicationPackage = applicationPackage;
    }

    public final long getSessionId() { return sessionId; }

    public Session.Status getStatus() {
        return sessionZooKeeperClient.readStatus();
    }

    @Override
    public String toString() {
        return "Session,id=" + sessionId + ",status=" + getStatus();
    }

    public long getActiveSessionAtCreate() {
        return getMetaData().getPreviousActiveGeneration();
    }

    /**
     * The status of this session.
     */
    public enum Status {
        NEW, PREPARE, ACTIVATE, DEACTIVATE, UNKNOWN, DELETE;

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

    public Instant getActivatedTime() {
        return sessionZooKeeperClient.readActivatedTime();
    }

    public void setApplicationId(ApplicationId applicationId) {
        sessionZooKeeperClient.writeApplicationId(applicationId);
    }

    public void setTags(Tags tags) {
        sessionZooKeeperClient.writeTags(tags);
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

    public void setTenantSecretStores(List<TenantSecretStore> tenantSecretStores) {
        sessionZooKeeperClient.writeTenantSecretStores(tenantSecretStores);
    }

    public void setOperatorCertificates(List<X509Certificate> operatorCertificates) {
        sessionZooKeeperClient.writeOperatorCertificates(operatorCertificates);
    }

    public void setCloudAccount(Optional<CloudAccount> cloudAccount) {
        sessionZooKeeperClient.writeCloudAccount(cloudAccount);
    }

    /** Returns application id read from ZooKeeper. Will throw RuntimeException if not found */
    public ApplicationId getApplicationId() {
        return sessionZooKeeperClient.readApplicationId()
                .orElseThrow(() -> new NotFoundException("Unable to read application id for session " + sessionId));
    }

    public Tags getTags() {
        return sessionZooKeeperClient.readTags();
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

    public List<TenantSecretStore> getTenantSecretStores() {
        return sessionZooKeeperClient.readTenantSecretStores();
    }

    public List<X509Certificate> getOperatorCertificates() {
        return sessionZooKeeperClient.readOperatorCertificates();
    }

    public Optional<CloudAccount> getCloudAccount() {
        return sessionZooKeeperClient.readCloudAccount();
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

    public ApplicationFile getApplicationFile(Path relativePath, LocalSession.Mode mode) {
        if (mode.equals(Session.Mode.WRITE)) {
            markSessionEdited();
        }
        return getApplicationPackage().getFile(relativePath);
    }

    Optional<ApplicationSet> applicationSet() { return Optional.empty(); };

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
