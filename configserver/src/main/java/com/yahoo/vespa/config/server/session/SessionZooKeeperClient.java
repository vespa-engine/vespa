// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.Quota;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.model.api.TenantVault;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.DataplaneToken;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.text.Utf8;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.NotFoundException;
import com.yahoo.vespa.config.server.UserConfigDefinitionRepo;
import com.yahoo.vespa.config.server.filedistribution.AddFileInterface;
import com.yahoo.vespa.config.server.filedistribution.MockFileManager;
import com.yahoo.vespa.config.server.session.Session.Status;
import com.yahoo.vespa.config.server.tenant.CloudAccountSerializer;
import com.yahoo.vespa.config.server.tenant.DataplaneTokenSerializer;
import com.yahoo.vespa.config.server.tenant.OperatorCertificateSerializer;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TenantSecretStoreSerializer;
import com.yahoo.vespa.config.server.tenant.TenantVaultSerializer;
import com.yahoo.vespa.config.server.zookeeper.ZKApplication;
import com.yahoo.vespa.config.server.zookeeper.ZKApplicationPackage;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import org.apache.zookeeper.data.Stat;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

import static com.yahoo.vespa.config.server.session.SessionData.ACTIVATION_TRIGGERS_PATH;
import static com.yahoo.vespa.config.server.session.SessionData.APPLICATION_ID_PATH;
import static com.yahoo.vespa.config.server.session.SessionData.APPLICATION_PACKAGE_REFERENCE_PATH;
import static com.yahoo.vespa.config.server.session.SessionData.ATHENZ_DOMAIN;
import static com.yahoo.vespa.config.server.session.SessionData.CLOUD_ACCOUNT_PATH;
import static com.yahoo.vespa.config.server.session.SessionData.CREATE_TIME_PATH;
import static com.yahoo.vespa.config.server.session.SessionData.DATAPLANE_TOKENS_PATH;
import static com.yahoo.vespa.config.server.session.SessionData.DOCKER_IMAGE_REPOSITORY_PATH;
import static com.yahoo.vespa.config.server.session.SessionData.OPERATOR_CERTIFICATES_PATH;
import static com.yahoo.vespa.config.server.session.SessionData.QUOTA_PATH;
import static com.yahoo.vespa.config.server.session.SessionData.SESSION_DATA_PATH;
import static com.yahoo.vespa.config.server.session.SessionData.TENANT_SECRET_STORES_PATH;
import static com.yahoo.vespa.config.server.session.SessionData.TENANT_VAULTS_PATH;
import static com.yahoo.vespa.config.server.session.SessionData.VERSION_PATH;
import static com.yahoo.vespa.config.server.zookeeper.ZKApplication.USER_DEFCONFIGS_ZK_SUBPATH;
import static com.yahoo.vespa.curator.Curator.CompletionWaiter;
import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Zookeeper client for a specific session. Path for a session is /config/v2/tenants/&lt;tenant&gt;/sessions/&lt;sessionid&gt;
 * Can be used to read and write session status and create and get prepare and active barrier.
 * 
 * @author Ulf Lilleengen
 */
public class SessionZooKeeperClient {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(SessionZooKeeperClient.class.getName());

    // NOTE: Any state added here MUST also be propagated in com.yahoo.vespa.config.server.deploy.Deployment.prepare()

    private final Curator curator;
    private final TenantName tenantName;
    private final long sessionId;
    private final Path sessionPath;
    private final Path sessionStatusPath;
    private final String serverId;  // hostname
    private final int maxNodeSize;
    private final AddFileInterface fileManager;
    private final Duration barrierWaitForAllTimeout;

    public SessionZooKeeperClient(Curator curator, TenantName tenantName, long sessionId, ConfigserverConfig configserverConfig, AddFileInterface fileManager, int maxNodeSize) {
        this.curator = curator;
        this.tenantName = tenantName;
        this.sessionId = sessionId;
        this.sessionPath = getSessionPath(tenantName, sessionId);
        this.serverId = configserverConfig.serverId();
        this.sessionStatusPath = sessionPath.append(ZKApplication.SESSIONSTATE_ZK_SUBPATH);
        this.maxNodeSize = maxNodeSize;
        this.fileManager = fileManager;
        this.barrierWaitForAllTimeout = Duration.ofSeconds(configserverConfig.barrierWaitForAllTimeout());
    }

    // For testing only
    public SessionZooKeeperClient(Curator curator, TenantName tenantName, long sessionId, ConfigserverConfig configserverConfig) {
        this(curator, tenantName, sessionId, configserverConfig, new MockFileManager(), 10 * 1024 * 1024);
    }

    public void writeStatus(Session.Status sessionStatus) {
        try {
            createWriteStatusTransaction(sessionStatus).commit();
        } catch (Exception e) {
            throw new RuntimeException("Unable to write session status", e);
        }
    }

    public Session.Status readStatus() {
        try {
            Optional<byte[]> data = curator.getData(sessionStatusPath);
            return data.map(d -> Session.Status.parse(Utf8.toString(d))).orElse(Session.Status.UNKNOWN);
        } catch (Exception e) {
            log.log(Level.INFO, "Failed to read session status from " + sessionStatusPath.getAbsolute() +
                    ", returning session status 'unknown'");
            return Session.Status.UNKNOWN;
        }
    }

    public long sessionId() { return sessionId; }

    public CompletionWaiter createActiveWaiter() { return createCompletionWaiter(barrierPath(ACTIVE_BARRIER)); }

    CompletionWaiter createPrepareWaiter() { return createCompletionWaiter(barrierPath(PREPARE_BARRIER)); }

    CompletionWaiter getPrepareWaiter() { return getCompletionWaiter(barrierPath(PREPARE_BARRIER)); }

    CompletionWaiter getActiveWaiter() { return getCompletionWaiter(barrierPath(ACTIVE_BARRIER)); }

    CompletionWaiter getUploadWaiter() { return getCompletionWaiter(barrierPath(UPLOAD_BARRIER)); }

    private static final String PREPARE_BARRIER = "prepareBarrier";
    private static final String ACTIVE_BARRIER = "activeBarrier";
    private static final String UPLOAD_BARRIER = "uploadBarrier";

    private Path barrierPath(String barrierName) {
        return sessionPath.append(barrierName);
    }

    private CompletionWaiter createCompletionWaiter(Path path) {
        return curator.createCompletionWaiter(path, serverId, barrierWaitForAllTimeout);
    }

    private CompletionWaiter getCompletionWaiter(Path path) {
        return curator.getCompletionWaiter(path, serverId, barrierWaitForAllTimeout);
    }

    /** Returns a transaction deleting this session on commit */
    public CuratorTransaction deleteTransaction() {
        return CuratorTransaction.from(CuratorOperations.deleteAll(sessionPath.getAbsolute(), curator), curator);
    }

    public ApplicationPackage loadApplicationPackage() {
        return new ZKApplicationPackage(fileManager, curator, sessionPath, maxNodeSize);
    }

    public ConfigDefinitionRepo getUserConfigDefinitions() {
        return new UserConfigDefinitionRepo(curator, sessionPath.append(USER_DEFCONFIGS_ZK_SUBPATH));
    }

    private Path applicationIdPath() {
        return sessionPath.append(APPLICATION_ID_PATH);
    }

    public void writeApplicationId(ApplicationId id) {
        if ( ! id.tenant().equals(tenantName))
            throw new IllegalArgumentException("Cannot write application id '" + id + "' for tenant '" + tenantName + "'");
        curator.set(applicationIdPath(), Utf8.toBytes(id.serializedForm()));
    }

    public ApplicationId readApplicationId() {
        return curator.getData(applicationIdPath()).map(d -> ApplicationId.fromSerializedForm(Utf8.toString(d)))
                      .orElseThrow(() -> new NotFoundException("Could not find application id for session " + sessionId));
    }

    void writeApplicationPackageReference(Optional<FileReference> applicationPackageReference) {
        applicationPackageReference.ifPresent(
                reference -> curator.set(applicationPackageReferencePath(), Utf8.toBytes(reference.value())));
    }

    Optional<FileReference> readApplicationPackageReference() {
        return curator.getData(applicationPackageReferencePath()).map(d -> new FileReference(Utf8.toString(d)));
    }

    private Path applicationPackageReferencePath() {
        return sessionPath.append(APPLICATION_PACKAGE_REFERENCE_PATH);
    }

    private Path versionPath() {
        return sessionPath.append(VERSION_PATH);
    }

    private Path dockerImageRepositoryPath() {
        return sessionPath.append(DOCKER_IMAGE_REPOSITORY_PATH);
    }

    private Path athenzDomainPath() {
        return sessionPath.append(ATHENZ_DOMAIN);
    }

    private Path quotaPath() {
        return sessionPath.append(QUOTA_PATH);
    }

    private Path tenantVaultPath() {
        return sessionPath.append(TENANT_VAULTS_PATH);
    }

    private Path tenantSecretStorePath() {
        return sessionPath.append(TENANT_SECRET_STORES_PATH);
    }

    private Path operatorCertificatesPath() {
        return sessionPath.append(OPERATOR_CERTIFICATES_PATH);
    }

    private Path cloudAccountPath() {
        return sessionPath.append(CLOUD_ACCOUNT_PATH);
    }

    private Path dataplaneTokensPath() {
        return sessionPath.append(DATAPLANE_TOKENS_PATH);
    }

    public void writeVespaVersion(Version version) {
       curator.set(versionPath(), Utf8.toBytes(version.toString()));
    }

    public void writeSessionData(SessionData sessionData) {
        curator.set(sessionPath.append(SESSION_DATA_PATH), sessionData.toJson());
    }

    public SessionData readSessionData() {
        return SessionData.fromSlime(SlimeUtils.jsonToSlime(curator.getData(sessionPath.append(SESSION_DATA_PATH)).orElseThrow()));
    }

    public boolean sessionDataExists() { return curator.exists(sessionPath.append(SESSION_DATA_PATH)); }

    public Version readVespaVersion() {
        Optional<byte[]> data = curator.getData(versionPath());
        // TODO: Empty version should not be possible any more - verify and remove
        return data.map(d -> new Version(Utf8.toString(d)))
                   .orElseGet(() -> {
                       log.log(Level.WARNING, "No Vespa version found for session at " + versionPath().getAbsolute() + "," + "returning current Vtag version");
                       return Vtag.currentVersion;
                   });
    }

    public Optional<DockerImage> readDockerImageRepository() {
        Optional<byte[]> dockerImageRepository = curator.getData(dockerImageRepositoryPath());
        return dockerImageRepository.map(d -> DockerImage.fromString(Utf8.toString(d)));
    }

    public void writeDockerImageRepository(Optional<DockerImage> dockerImageRepository) {
        dockerImageRepository.ifPresent(repo -> curator.set(dockerImageRepositoryPath(), Utf8.toBytes(repo.untagged())));
    }

    public Instant readCreateTime() {
        // TODO jonmv: clean up
        Optional<byte[]> data = curator.getData(getCreateTimePath());
        return data.map(d -> Instant.ofEpochSecond(Long.parseLong(Utf8.toString(d))))
                   .or(() -> {
                       RuntimeException stack = Math.random() < 1e-4 ? new RuntimeException("Trace log") : null;
                       log.log(Level.FINE, stack, () -> "No creation time found for session at " + getCreateTimePath().getAbsolute() + ", returning session path ctime");
                       return curator.getStat(sessionPath).map(s -> Instant.ofEpochMilli(s.getCtime()));
                   })
                   .orElseGet(() -> {
                       log.log(Level.FINE, () -> "No ZK ctime found for session at " + sessionPath.getAbsolute() + ", returning epoch");
                       return Instant.EPOCH;
                   });
    }

    public Instant readActivatedTime() {
        Optional<Stat> statData = curator.getStat(sessionStatusPath);
        return statData.map(s -> Instant.ofEpochMilli(s.getMtime())).orElse(Instant.EPOCH);
    }

    private Path getCreateTimePath() {
        return sessionPath.append(CREATE_TIME_PATH);
    }

    AllocatedHosts getAllocatedHosts() {
        return loadApplicationPackage().getAllocatedHosts()
                                       .orElseThrow(() -> new IllegalStateException("Allocated hosts does not exists"));
    }

    public Transaction createWriteStatusTransaction(Session.Status status) {
        CuratorTransaction transaction = new CuratorTransaction(curator);
        if (curator.exists(sessionStatusPath)) {
            transaction.add(CuratorOperations.setData(sessionStatusPath.getAbsolute(), Utf8.toBytes(status.name())));
        } else {
            transaction.add(CuratorOperations.create(sessionStatusPath.getAbsolute(), Utf8.toBytes(status.name())));
        }
        return transaction;
    }

    public void writeAthenzDomain(Optional<AthenzDomain> athenzDomain) {
        athenzDomain.ifPresent(domain -> curator.set(athenzDomainPath(), Utf8.toBytes(domain.value())));
    }

    public Optional<AthenzDomain> readAthenzDomain() {
        return curator.getData(athenzDomainPath())
                      .map(Utf8::toString)
                      .filter(domain -> !domain.isBlank())
                      .map(AthenzDomain::from);
    }

    public void writeQuota(Optional<Quota> maybeQuota) {
        maybeQuota.ifPresent(quota -> {
            var bytes = uncheck(() -> SlimeUtils.toJsonBytes(quota.toSlime()));
            curator.set(quotaPath(), bytes);
        });
    }

    public Optional<Quota> readQuota() {
        return curator.getData(quotaPath())
                      .map(SlimeUtils::jsonToSlime)
                      .map(slime -> Quota.fromSlime(slime.get()));
    }

    public void writeTenantVaults(List<TenantVault> tenantVaults) {
        if (! tenantVaults.isEmpty()) {
            var bytes = uncheck(() -> SlimeUtils.toJsonBytes(TenantVaultSerializer.toSlime(tenantVaults)));
            curator.set(tenantVaultPath(), bytes);
        }
    }

    public List<TenantVault> readTenantVaults() {
        return curator.getData(tenantVaultPath())
                .map(SlimeUtils::jsonToSlime)
                .map(slime -> TenantVaultSerializer.listFromSlime(slime.get()))
                .orElse(List.of());
    }

    public void writeTenantSecretStores(List<TenantSecretStore> tenantSecretStores) {
        if (!tenantSecretStores.isEmpty()) {
            var bytes = uncheck(() -> SlimeUtils.toJsonBytes(TenantSecretStoreSerializer.toSlime(tenantSecretStores)));
            curator.set(tenantSecretStorePath(), bytes);
        }
    }

    public List<TenantSecretStore> readTenantSecretStores() {
        return curator.getData(tenantSecretStorePath())
                      .map(SlimeUtils::jsonToSlime)
                      .map(slime -> TenantSecretStoreSerializer.listFromSlime(slime.get()))
                      .orElse(List.of());
    }

    public void writeOperatorCertificates(List<X509Certificate> certificates) {
        if( ! certificates.isEmpty()) {
            var bytes = uncheck(() -> SlimeUtils.toJsonBytes(OperatorCertificateSerializer.toSlime(certificates)));
            curator.set(operatorCertificatesPath(), bytes);
        }
    }

    public List<X509Certificate> readOperatorCertificates() {
        return curator.getData(operatorCertificatesPath())
                      .map(SlimeUtils::jsonToSlime)
                      .map(slime -> OperatorCertificateSerializer.fromSlime(slime.get()))
                      .orElse(List.of());
    }

    public void writeCloudAccount(Optional<CloudAccount> cloudAccount) {
        if (cloudAccount.isPresent()) {
            byte[] data = uncheck(() -> SlimeUtils.toJsonBytes(CloudAccountSerializer.toSlime(cloudAccount.get())));
            curator.set(cloudAccountPath(), data);
        } else {
            curator.delete(cloudAccountPath());
        }
    }

    public Optional<CloudAccount> readCloudAccount() {
        return curator.getData(cloudAccountPath()).map(SlimeUtils::jsonToSlime).map(slime -> CloudAccountSerializer.fromSlime(slime.get()));
    }

    public void writeDataplaneTokens(List<DataplaneToken> dataplaneTokens) {
        byte[] data = uncheck(() -> SlimeUtils.toJsonBytes(DataplaneTokenSerializer.toSlime(dataplaneTokens)));
        curator.set(dataplaneTokensPath(), data);
    }

    public List<DataplaneToken> readDataplaneTokens() {
        return curator.getData(dataplaneTokensPath())
                .map(SlimeUtils::jsonToSlime)
                .map(slime -> DataplaneTokenSerializer.fromSlime(slime.get()))
                .orElse(List.of());
    }

    public void writeActivationTriggers(ActivationTriggers activationTriggers) {
        curator.set(sessionPath.append(ACTIVATION_TRIGGERS_PATH), ActivationTriggersSerializer.toJson(activationTriggers));
    }

    public ActivationTriggers readActivationTriggers() {
        return curator.getData(sessionPath.append(ACTIVATION_TRIGGERS_PATH))
                      .map(ActivationTriggersSerializer::fromJson)
                      .orElseGet(() -> {
                          log.log(Level.WARNING, "No activation triggers found for session at " + sessionPath.append(ACTIVATION_TRIGGERS_PATH).getAbsolute() + ", returning empty");
                          return ActivationTriggers.empty();
                      });
    }

    /**
     * Create necessary paths atomically for a new session.
     *
     * @param createTime Time of session creation.
     */
    public void createNewSession(Instant createTime) {
        log.log(Level.FINE, () -> "Creating new session at " + sessionPath.getAbsolute());
        CuratorTransaction transaction = new CuratorTransaction(curator);
        transaction.add(CuratorOperations.create(sessionPath.getAbsolute()));
        transaction.add(CuratorOperations.create(sessionPath.append(UPLOAD_BARRIER).getAbsolute()));
        transaction.add(CuratorOperations.create(sessionStatusPath.getAbsolute(), Utf8.toBytes(Status.NEW.name())));
        transaction.add(CuratorOperations.create(getCreateTimePath().getAbsolute(), Utf8.toBytes(String.valueOf(createTime.getEpochSecond()))));
        transaction.commit();
        log.log(Level.FINE, () -> "Done creating new session at " + sessionPath.getAbsolute());
    }

    public static Path getSessionPath(TenantName tenantName, long sessionId) {
        return TenantRepository.getSessionsPath(tenantName).append(String.valueOf(sessionId));
    }
}
