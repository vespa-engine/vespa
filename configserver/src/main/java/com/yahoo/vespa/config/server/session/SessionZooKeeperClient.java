// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.Quota;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.text.Utf8;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.UserConfigDefinitionRepo;
import com.yahoo.vespa.config.server.deploy.ZooKeeperClient;
import com.yahoo.vespa.config.server.deploy.ZooKeeperDeployer;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TenantSecretStoreSerializer;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.config.server.zookeeper.ZKApplicationPackage;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

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

    static final String APPLICATION_ID_PATH = "applicationId";
    static final String APPLICATION_PACKAGE_REFERENCE_PATH = "applicationPackageReference";
    private static final String VERSION_PATH = "version";
    private static final String CREATE_TIME_PATH = "createTime";
    private static final String DOCKER_IMAGE_REPOSITORY_PATH = "dockerImageRepository";
    private static final String ATHENZ_DOMAIN = "athenzDomain";
    private static final String QUOTA_PATH = "quota";
    private static final String TENANT_SECRET_STORES_PATH = "tenantSecretStores";

    private final Curator curator;
    private final ConfigCurator configCurator;
    private final TenantName tenantName;
    private final Path sessionPath;
    private final Path sessionStatusPath;
    private final String serverId;  // hostname

    public SessionZooKeeperClient(Curator curator,
                                  ConfigCurator configCurator,
                                  TenantName tenantName,
                                  long sessionId,
                                  String serverId) {
        this.curator = curator;
        this.configCurator = configCurator;
        this.tenantName = tenantName;
        this.sessionPath = getSessionPath(tenantName, sessionId);
        this.serverId = serverId;
        this.sessionStatusPath = sessionPath.append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH);
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
            String data = configCurator.getData(sessionStatusPath.getAbsolute());
            return Session.Status.parse(data);
        } catch (Exception e) {
            log.log(Level.INFO, "Unable to read session status, assuming it was deleted");
            return Session.Status.NONE;
        }
    }

    Curator.CompletionWaiter createPrepareWaiter() {
        return createCompletionWaiter(PREPARE_BARRIER);
    }

    public Curator.CompletionWaiter createActiveWaiter() {
        return createCompletionWaiter(ACTIVE_BARRIER);
    }

    Curator.CompletionWaiter getPrepareWaiter() {
        return getCompletionWaiter(getWaiterPath(PREPARE_BARRIER));
    }

    Curator.CompletionWaiter getActiveWaiter() {
        return getCompletionWaiter(getWaiterPath(ACTIVE_BARRIER));
    }

    Curator.CompletionWaiter getUploadWaiter() { return getCompletionWaiter(getWaiterPath(UPLOAD_BARRIER)); }

    private static final String PREPARE_BARRIER = "prepareBarrier";
    private static final String ACTIVE_BARRIER = "activeBarrier";
    private static final String UPLOAD_BARRIER = "uploadBarrier";

    private Path getWaiterPath(String barrierName) {
        return sessionPath.append(barrierName);
    }

    /** Returns the number of node members needed in a barrier */
    private int getNumberOfMembers() {
        return (curator.zooKeeperEnsembleCount() / 2) + 1; // majority
    }

    private Curator.CompletionWaiter createCompletionWaiter(String waiterNode) {
        return curator.createCompletionWaiter(sessionPath, waiterNode, getNumberOfMembers(), serverId);
    }

    private Curator.CompletionWaiter getCompletionWaiter(Path path) {
        return curator.getCompletionWaiter(path, getNumberOfMembers(), serverId);
    }

    /** Returns a transaction deleting this session on commit */
    public CuratorTransaction deleteTransaction() {
        return CuratorTransaction.from(CuratorOperations.deleteAll(sessionPath.getAbsolute(), curator), curator);
    }

    public ApplicationPackage loadApplicationPackage() {
        return new ZKApplicationPackage(configCurator, sessionPath);
    }

    public ConfigDefinitionRepo getUserConfigDefinitions() {
        return new UserConfigDefinitionRepo(configCurator, sessionPath.append(ConfigCurator.USER_DEFCONFIGS_ZK_SUBPATH).getAbsolute());
    }

    private String applicationIdPath() {
        return sessionPath.append(APPLICATION_ID_PATH).getAbsolute();
    }

    public void writeApplicationId(ApplicationId id) {
        if ( ! id.tenant().equals(tenantName))
            throw new IllegalArgumentException("Cannot write application id '" + id + "' for tenant '" + tenantName + "'");
        configCurator.putData(applicationIdPath(), id.serializedForm());
    }

    public Optional<ApplicationId> readApplicationId() {
        String idString = configCurator.getData(applicationIdPath());
        return (idString == null)
                ? Optional.empty()
                : Optional.of(ApplicationId.fromSerializedForm(idString));
    }

    void writeApplicationPackageReference(Optional<FileReference> applicationPackageReference) {
        applicationPackageReference.ifPresent(
                reference -> configCurator.putData(applicationPackageReferencePath(), reference.value()));
    }

    FileReference readApplicationPackageReference() {
        if ( ! configCurator.exists(applicationPackageReferencePath())) return null;  // This should not happen.
        return new FileReference(configCurator.getData(applicationPackageReferencePath()));
    }

    private String applicationPackageReferencePath() {
        return sessionPath.append(APPLICATION_PACKAGE_REFERENCE_PATH).getAbsolute();
    }

    private String versionPath() {
        return sessionPath.append(VERSION_PATH).getAbsolute();
    }

    private String dockerImageRepositoryPath() {
        return sessionPath.append(DOCKER_IMAGE_REPOSITORY_PATH).getAbsolute();
    }

    private String athenzDomainPath() {
        return sessionPath.append(ATHENZ_DOMAIN).getAbsolute();
    }

    private String quotaPath() {
        return sessionPath.append(QUOTA_PATH).getAbsolute();
    }

    private String tenantSecretStorePath() {
        return sessionPath.append(TENANT_SECRET_STORES_PATH).getAbsolute();
    }

    public void writeVespaVersion(Version version) {
        configCurator.putData(versionPath(), version.toString());
    }

    public Version readVespaVersion() {
        if ( ! configCurator.exists(versionPath())) return Vtag.currentVersion; // TODO: This should not be possible any more - verify and remove
        return new Version(configCurator.getData(versionPath()));
    }

    public Optional<DockerImage> readDockerImageRepository() {
        if ( ! configCurator.exists(dockerImageRepositoryPath())) return Optional.empty();
        String dockerImageRepository = configCurator.getData(dockerImageRepositoryPath());
        return dockerImageRepository.isEmpty() ? Optional.empty() : Optional.of(DockerImage.fromString(dockerImageRepository));
    }

    public void writeDockerImageRepository(Optional<DockerImage> dockerImageRepository) {
        dockerImageRepository.ifPresent(repo -> configCurator.putData(dockerImageRepositoryPath(), repo.untagged()));
    }

    public Instant readCreateTime() {
        String path = getCreateTimePath();
        if ( ! configCurator.exists(path)) return Instant.EPOCH;
        return Instant.ofEpochSecond(Long.parseLong(configCurator.getData(path)));
    }

    private String getCreateTimePath() {
        return sessionPath.append(CREATE_TIME_PATH).getAbsolute();
    }

    AllocatedHosts getAllocatedHosts() {
        return loadApplicationPackage().getAllocatedHosts()
                                       .orElseThrow(() -> new IllegalStateException("Allocated hosts does not exists"));
    }

    public ZooKeeperDeployer createDeployer(DeployLogger logger) {
        ZooKeeperClient zkClient = new ZooKeeperClient(configCurator, logger, sessionPath);
        return new ZooKeeperDeployer(zkClient);
    }

    public Transaction createWriteStatusTransaction(Session.Status status) {
        String path = sessionStatusPath.getAbsolute();
        CuratorTransaction transaction = new CuratorTransaction(curator);
        if (configCurator.exists(path)) {
            transaction.add(CuratorOperations.setData(sessionStatusPath.getAbsolute(), Utf8.toBytes(status.name())));
        } else {
            transaction.add(CuratorOperations.create(sessionStatusPath.getAbsolute(), Utf8.toBytes(status.name())));
        }
        return transaction;
    }

    public void writeAthenzDomain(Optional<AthenzDomain> athenzDomain) {
        athenzDomain.ifPresent(domain -> configCurator.putData(athenzDomainPath(), domain.value()));
    }

    public Optional<AthenzDomain> readAthenzDomain() {
        if ( ! configCurator.exists(athenzDomainPath())) return Optional.empty();
        return Optional.ofNullable(configCurator.getData(athenzDomainPath()))
                .filter(domain -> ! domain.isBlank())
                .map(AthenzDomain::from);
    }

    public void writeQuota(Optional<Quota> maybeQuota) {
        maybeQuota.ifPresent(quota -> {
            var bytes = uncheck(() -> SlimeUtils.toJsonBytes(quota.toSlime()));
            configCurator.putData(quotaPath(), bytes);
        });
    }

    public Optional<Quota> readQuota() {
        if ( ! configCurator.exists(quotaPath())) return Optional.empty();
        return Optional.ofNullable(configCurator.getData(quotaPath()))
                .map(SlimeUtils::jsonToSlime)
                .map(slime -> Quota.fromSlime(slime.get()));
    }

    public void writeTenantSecretStores(List<TenantSecretStore> tenantSecretStores) {
        if (!tenantSecretStores.isEmpty()) {
            var bytes = uncheck(() -> SlimeUtils.toJsonBytes(TenantSecretStoreSerializer.toSlime(tenantSecretStores)));
            configCurator.putData(tenantSecretStorePath(), bytes);
        }

    }

    public List<TenantSecretStore> readTenantSecretStores() {
        if ( ! configCurator.exists(tenantSecretStorePath())) return List.of();
        return Optional.ofNullable(configCurator.getData(tenantSecretStorePath()))
                .map(SlimeUtils::jsonToSlime)
                .map(slime -> TenantSecretStoreSerializer.listFromSlime(slime.get()))
                .orElse(List.of());
    }

    /**
     * Create necessary paths atomically for a new session.
     *
     * @param createTime Time of session creation.
     */
    public void createNewSession(Instant createTime) {
        CuratorTransaction transaction = new CuratorTransaction(curator);
        transaction.add(CuratorOperations.create(sessionPath.getAbsolute()));
        transaction.add(CuratorOperations.create(sessionPath.append(UPLOAD_BARRIER).getAbsolute()));
        transaction.add(createWriteStatusTransaction(Session.Status.NEW).operations());
        transaction.add(CuratorOperations.create(getCreateTimePath(), Utf8.toBytes(String.valueOf(createTime.getEpochSecond()))));
        transaction.commit();
    }

    private static Path getSessionPath(TenantName tenantName, long sessionId) {
        return TenantRepository.getSessionsPath(tenantName).append(String.valueOf(sessionId));
    }

}
