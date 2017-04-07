// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.ProvisionInfo;
import com.yahoo.config.provision.TenantName;
import com.yahoo.transaction.Transaction;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.StaticConfigDefinitionRepo;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.deploy.ZooKeeperClient;
import com.yahoo.vespa.config.server.deploy.ZooKeeperDeployer;
import com.yahoo.vespa.config.server.zookeeper.ZKApplicationPackage;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;

import java.util.concurrent.TimeUnit;

/**
 * Zookeeper client for a specific session. Can be used to read and write session status
 * and create and get prepare and active barrier.
 *
 * @author lulf
 * @since 5.1
 */
public class SessionZooKeeperClient {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(SessionZooKeeperClient.class.getName());
    static final String APPLICATION_ID_PATH = "applicationId";
    static final String VERSION_PATH = "version";
    static final String CREATE_TIME_PATH = "createTime";
    private final Curator curator;
    private final ConfigCurator configCurator;
    private final Path rootPath;
    private final Path sessionStatusPath;
    private final String serverId;
    private final ServerCacheLoader cacheLoader;

    // Only for testing when cache loader does not need cache entries.
    public SessionZooKeeperClient(Curator curator, Path rootPath) {
        this(curator, ConfigCurator.create(curator), rootPath, new StaticConfigDefinitionRepo(), "");
    }

    public SessionZooKeeperClient(Curator curator, ConfigCurator configCurator, Path rootPath, ConfigDefinitionRepo definitionRepo, String serverId) {
        this.curator = curator;
        this.configCurator = configCurator;
        this.rootPath = rootPath;
        this.serverId = serverId;
        this.sessionStatusPath = rootPath.append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH);
        this.cacheLoader = new ServerCacheLoader(configCurator, rootPath, definitionRepo);
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
            log.log(LogLevel.INFO, "Unable to read session status, assuming it was deleted");
            return Session.Status.NONE;
        }
    }

    Curator.CompletionWaiter createPrepareWaiter() {
        return createCompletionWaiter(PREPARE_BARRIER);
    }

    Curator.CompletionWaiter createActiveWaiter() {
        return createCompletionWaiter(ACTIVE_BARRIER);
    }

    Curator.CompletionWaiter getPrepareWaiter() {
        return getCompletionWaiter(getWaiterPath(PREPARE_BARRIER));
    }

    Curator.CompletionWaiter getActiveWaiter() {
        return getCompletionWaiter(getWaiterPath(ACTIVE_BARRIER));
    }

    Curator.CompletionWaiter getUploadWaiter() {
        return getCompletionWaiter(getWaiterPath(UPLOAD_BARRIER));
    }

    private static final String PREPARE_BARRIER = "prepareBarrier";
    private static final String ACTIVE_BARRIER = "activeBarrier";
    private static final String UPLOAD_BARRIER = "uploadBarrier";

    private Path getWaiterPath(String barrierName) {
        return rootPath.append(barrierName);
    }

    /** Returns the number of node members needed in a barrier */
    private int getNumberOfMembers() {
        return (curator.serverCount() / 2) + 1; // majority
    }

    private Curator.CompletionWaiter createCompletionWaiter(String waiterNode) {
        return curator.createCompletionWaiter(rootPath, waiterNode, getNumberOfMembers(), serverId);
    }

    private Curator.CompletionWaiter getCompletionWaiter(Path path) {
        return curator.getCompletionWaiter(path, getNumberOfMembers(), serverId);
    }

    public void delete() {
        try {
            log.log(LogLevel.DEBUG, "Deleting " + rootPath.getAbsolute());
            configCurator.deleteRecurse(rootPath.getAbsolute());
        } catch (RuntimeException e) {
            log.log(LogLevel.INFO, "Error deleting session (" + rootPath.getAbsolute() + ") from zookeeper");
        }
    }

    /** Returns a transaction deleting this session on commit */
    public CuratorTransaction deleteTransaction() {
        return CuratorTransaction.from(CuratorOperations.deleteAll(rootPath.getAbsolute(), curator), curator);
    }

    public ApplicationPackage loadApplicationPackage() {
        return new ZKApplicationPackage(configCurator, rootPath);
    }

    public ServerCache loadServerCache() {
        return cacheLoader.loadCache();
    }

    private String applicationIdPath() {
        return rootPath.append(APPLICATION_ID_PATH).getAbsolute();
    }

    public void writeApplicationId(ApplicationId id) {
        configCurator.putData(applicationIdPath(), id.serializedForm());
    }

    public ApplicationId readApplicationId() {
        if ( ! configCurator.exists(applicationIdPath())) return ApplicationId.defaultId();
        return ApplicationId.fromSerializedForm(configCurator.getData(applicationIdPath()));
    }

    private String versionPath() {
        return rootPath.append(VERSION_PATH).getAbsolute();
    }

    public void writeVespaVersion(Version version) {
        configCurator.putData(versionPath(), version.toString());
    }

    public Version readVespaVersion() {
        if ( ! configCurator.exists(versionPath())) return Vtag.currentVersion;
        return new Version(configCurator.getData(versionPath()));
    }

    // in seconds
    public long readCreateTime() {
        String path = getCreateTimePath();
        if ( ! configCurator.exists(path)) return 0L;
        return Long.parseLong(configCurator.getData(path));
    }

    private String getCreateTimePath() {
        return rootPath.append(CREATE_TIME_PATH).getAbsolute();
    }

    ProvisionInfo getProvisionInfo() {
        return loadApplicationPackage().getProvisionInfoMap().values().stream()
                .reduce((infoA, infoB) -> infoA.merge(infoB))
                .orElseThrow(() -> new IllegalStateException("Trying to read provision info, but no provision info exists"));
    }

    public ZooKeeperDeployer createDeployer(DeployLogger logger) {
        ZooKeeperClient zkClient = new ZooKeeperClient(configCurator, logger, true, rootPath);
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

    /**
     * Create necessary paths atomically for a new session.
     * @param createTime Time of session creation.
     * @param timeUnit Time unit of createTime.
     */
    public void createNewSession(long createTime, TimeUnit timeUnit) {
        CuratorTransaction transaction = new CuratorTransaction(curator);
        transaction.add(CuratorOperations.create(rootPath.getAbsolute()));
        transaction.add(CuratorOperations.create(rootPath.append(UPLOAD_BARRIER).getAbsolute()));
        transaction.add(createWriteStatusTransaction(Session.Status.NEW).operations());
        transaction.add(CuratorOperations.create(getCreateTimePath(), Utf8.toBytes(String.valueOf(timeUnit.toSeconds(createTime)))));
        transaction.commit();
    }

}
