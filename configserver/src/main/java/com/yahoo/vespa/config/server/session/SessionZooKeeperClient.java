// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.AllocatedHosts;
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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
    private static final String VERSION_PATH = "version";
    private static final String CREATE_TIME_PATH = "createTime";
    private final Curator curator;
    private final ConfigCurator configCurator;
    private final Path sessionPath;
    private final Path sessionStatusPath;
    private final String serverId;
    private final ServerCacheLoader cacheLoader;
    private final Optional<NodeFlavors> nodeFlavors;

    // Only for testing when cache loader does not need cache entries.
    public SessionZooKeeperClient(Curator curator, Path sessionPath) {
        this(curator, ConfigCurator.create(curator), sessionPath, new StaticConfigDefinitionRepo(), "", Optional.empty());
    }

    public SessionZooKeeperClient(Curator curator,
                                  ConfigCurator configCurator,
                                  Path sessionPath,
                                  ConfigDefinitionRepo definitionRepo,
                                  String serverId,
                                  Optional<NodeFlavors> nodeFlavors) {
        this.curator = curator;
        this.configCurator = configCurator;
        this.sessionPath = sessionPath;
        this.serverId = serverId;
        this.nodeFlavors = nodeFlavors;
        this.sessionStatusPath = sessionPath.append(ConfigCurator.SESSIONSTATE_ZK_SUBPATH);
        this.cacheLoader = new ServerCacheLoader(configCurator, sessionPath, definitionRepo);
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

    public void delete() {
        try {
            log.log(LogLevel.DEBUG, "Deleting " + sessionPath.getAbsolute());
            configCurator.deleteRecurse(sessionPath.getAbsolute());
        } catch (RuntimeException e) {
            log.log(LogLevel.INFO, "Error deleting session (" + sessionPath.getAbsolute() + ") from zookeeper");
        }
    }

    /** Returns a transaction deleting this session on commit */
    public CuratorTransaction deleteTransaction() {
        return CuratorTransaction.from(CuratorOperations.deleteAll(sessionPath.getAbsolute(), curator), curator);
    }

    public ApplicationPackage loadApplicationPackage() {
        return new ZKApplicationPackage(configCurator, sessionPath, nodeFlavors);
    }

    public ServerCache loadServerCache() {
        return cacheLoader.loadCache();
    }

    private String applicationIdPath() {
        return sessionPath.append(APPLICATION_ID_PATH).getAbsolute();
    }

    public void writeApplicationId(ApplicationId id) {
        configCurator.putData(applicationIdPath(), id.serializedForm());
    }

    public ApplicationId readApplicationId() {
        if ( ! configCurator.exists(applicationIdPath())) return ApplicationId.defaultId();
        return ApplicationId.fromSerializedForm(configCurator.getData(applicationIdPath()));
    }

    private String versionPath() {
        return sessionPath.append(VERSION_PATH).getAbsolute();
    }

    public void writeVespaVersion(Version version) {
        configCurator.putData(versionPath(), version.toString());
    }

    public Version readVespaVersion() {
        if ( ! configCurator.exists(versionPath())) return Vtag.currentVersion; // TODO: This should not be possible any more - verify and remove
        return new Version(configCurator.getData(versionPath()));
    }

    // in seconds
    public long readCreateTime() {
        String path = getCreateTimePath();
        if ( ! configCurator.exists(path)) return 0L;
        return Long.parseLong(configCurator.getData(path));
    }

    private String getCreateTimePath() {
        return sessionPath.append(CREATE_TIME_PATH).getAbsolute();
    }

    AllocatedHosts getAllocatedHosts() {
        return loadApplicationPackage().getAllocatedHosts()
                                       .orElseThrow(() -> new IllegalStateException("Allocated hosts does not exists"));
    }

    public ZooKeeperDeployer createDeployer(DeployLogger logger) {
        ZooKeeperClient zkClient = new ZooKeeperClient(configCurator, logger, true, sessionPath);
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
        transaction.add(CuratorOperations.create(sessionPath.getAbsolute()));
        transaction.add(CuratorOperations.create(sessionPath.append(UPLOAD_BARRIER).getAbsolute()));
        transaction.add(createWriteStatusTransaction(Session.Status.NEW).operations());
        transaction.add(CuratorOperations.create(getCreateTimePath(), Utf8.toBytes(String.valueOf(timeUnit.toSeconds(createTime)))));
        transaction.commit();
    }

}
