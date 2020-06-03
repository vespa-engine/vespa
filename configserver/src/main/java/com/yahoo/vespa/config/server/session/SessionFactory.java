// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.application.provider.*;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.io.IOUtils;
import java.util.logging.Level;
import com.yahoo.path.Path;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.host.HostValidator;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.zookeeper.SessionCounter;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.Flags;

import java.io.File;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Serves as the factory of sessions. Takes care of copying files to the correct folder and initializing the
 * session state.
 *
 * @author Ulf Lilleengen
 */
public class SessionFactory {

    private static final Logger log = Logger.getLogger(SessionFactory.class.getName());
    private static final long nonExistingActiveSession = 0;

    private final SessionPreparer sessionPreparer;
    private final Curator curator;
    private final ConfigCurator configCurator;
    private final TenantApplications applicationRepo;
    private final Path sessionsPath;
    private final GlobalComponentRegistry componentRegistry;
    private final HostValidator<ApplicationId> hostRegistry;
    private final TenantName tenant;
    private final String serverId;
    private final Optional<NodeFlavors> nodeFlavors;
    private final Clock clock;
    private final BooleanFlag distributeApplicationPackage;

    public SessionFactory(GlobalComponentRegistry globalComponentRegistry,
                          TenantApplications applicationRepo,
                          HostValidator<ApplicationId> hostRegistry,
                          TenantName tenant) {
        this.hostRegistry = hostRegistry;
        this.tenant = tenant;
        this.sessionPreparer = globalComponentRegistry.getSessionPreparer();
        this.curator = globalComponentRegistry.getCurator();
        this.configCurator = globalComponentRegistry.getConfigCurator();
        this.sessionsPath = TenantRepository.getSessionsPath(tenant);
        this.applicationRepo = applicationRepo;
        this.componentRegistry = globalComponentRegistry;
        this.serverId = globalComponentRegistry.getConfigserverConfig().serverId();
        this.nodeFlavors = globalComponentRegistry.getZone().nodeFlavors();
        this.clock = globalComponentRegistry.getClock();
        this.distributeApplicationPackage = Flags.CONFIGSERVER_DISTRIBUTE_APPLICATION_PACKAGE
                .bindTo(globalComponentRegistry.getFlagSource());
    }

    /**
     * Creates a new deployment session from an application package.
     *
     * @param applicationDirectory a File pointing to an application.
     * @param applicationId application id for this new session.
     * @param timeoutBudget Timeout for creating session and waiting for other servers.
     * @return a new session
     */
    public LocalSession createSession(File applicationDirectory, ApplicationId applicationId, TimeoutBudget timeoutBudget) {
        return create(applicationDirectory, applicationId, nonExistingActiveSession, false, timeoutBudget);
    }


    public RemoteSession createRemoteSession(long sessionId) {
        Path sessionPath = sessionsPath.append(String.valueOf(sessionId));
        SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionPath);
        return new RemoteSession(tenant, sessionId, componentRegistry, sessionZKClient);
    }

    private void ensureSessionPathDoesNotExist(long sessionId) {
        Path sessionPath = getSessionPath(sessionId);
        if (configCurator.exists(sessionPath.getAbsolute())) {
            throw new IllegalArgumentException("Path " + sessionPath.getAbsolute() + " already exists in ZooKeeper");
        }
    }

    private ApplicationPackage createApplication(File userDir,
                                                 File configApplicationDir,
                                                 ApplicationId applicationId,
                                                 long sessionId,
                                                 long currentlyActiveSessionId,
                                                 boolean internalRedeploy) {
        long deployTimestamp = System.currentTimeMillis();
        String user = System.getenv("USER");
        if (user == null) {
            user = "unknown";
        }
        DeployData deployData = new DeployData(user, userDir.getAbsolutePath(), applicationId, deployTimestamp, internalRedeploy, sessionId, currentlyActiveSessionId);
        return FilesApplicationPackage.fromFileWithDeployData(configApplicationDir, deployData);
    }

    private LocalSession createSessionFromApplication(ApplicationPackage applicationPackage,
                                                      long sessionId,
                                                      SessionZooKeeperClient sessionZKClient,
                                                      TimeoutBudget timeoutBudget,
                                                      Clock clock) {
        log.log(Level.FINE, TenantRepository.logPre(tenant) + "Creating session " + sessionId + " in ZooKeeper");
        sessionZKClient.createNewSession(clock.instant());
        Curator.CompletionWaiter waiter = sessionZKClient.getUploadWaiter();
        LocalSession session = new LocalSession(tenant, sessionId, sessionPreparer, applicationPackage, sessionZKClient,
                                                getSessionAppDir(sessionId), applicationRepo, hostRegistry);
        waiter.awaitCompletion(timeoutBudget.timeLeft());
        return session;
    }

    /**
     * Creates a new deployment session from an already existing session.
     *
     * @param existingSession the session to use as base
     * @param logger a deploy logger where the deploy log will be written.
     * @param internalRedeploy whether this session is for a system internal redeploy — not an application package change
     * @param timeoutBudget timeout for creating session and waiting for other servers.
     * @return a new session
     */
    public LocalSession createSessionFromExisting(Session existingSession,
                                                  DeployLogger logger,
                                                  boolean internalRedeploy,
                                                  TimeoutBudget timeoutBudget) {
        File existingApp = getSessionAppDir(existingSession.getSessionId());
        ApplicationId existingApplicationId = existingSession.getApplicationId();

        long activeSessionId = getActiveSessionId(existingApplicationId);
        logger.log(Level.FINE, "Create new session for application id '" + existingApplicationId + "' from existing active session " + activeSessionId);
        LocalSession session = create(existingApp, existingApplicationId, activeSessionId, internalRedeploy, timeoutBudget);
        // Note: Needs to be kept in sync with calls in SessionPreparer.writeStateToZooKeeper()
        session.setApplicationId(existingApplicationId);
        if (distributeApplicationPackage.value() && existingSession.getApplicationPackageReference() != null) {
            session.setApplicationPackageReference(existingSession.getApplicationPackageReference());
        }
        session.setVespaVersion(existingSession.getVespaVersion());
        session.setDockerImageRepository(existingSession.getDockerImageRepository());
        session.setAthenzDomain(existingSession.getAthenzDomain());
        return session;
    }

    private LocalSession create(File applicationFile, ApplicationId applicationId, long currentlyActiveSessionId,
                                boolean internalRedeploy, TimeoutBudget timeoutBudget) {
        long sessionId = getNextSessionId();
        try {
            ensureSessionPathDoesNotExist(sessionId);
            SessionZooKeeperClient sessionZooKeeperClient = createSessionZooKeeperClient(getSessionPath(sessionId));
            File userApplicationDir = getSessionAppDir(sessionId);
            IOUtils.copyDirectory(applicationFile, userApplicationDir);
            ApplicationPackage applicationPackage = createApplication(applicationFile,
                                                                      userApplicationDir,
                                                                      applicationId,
                                                                      sessionId,
                                                                      currentlyActiveSessionId,
                                                                      internalRedeploy);
            applicationPackage.writeMetaData();
            return createSessionFromApplication(applicationPackage, sessionId, sessionZooKeeperClient, timeoutBudget, clock);
        } catch (Exception e) {
            throw new RuntimeException("Error creating session " + sessionId, e);
        }
    }

    /**
     * Returns a new session instance for the given session id.
     */
    // TODO: rename
    public LocalSession loadSession(long sessionId) {
        File sessionDir = getAndValidateExistingSessionAppDir(sessionId);
        ApplicationPackage applicationPackage = FilesApplicationPackage.fromFile(sessionDir);
        Path sessionIdPath = sessionsPath.append(String.valueOf(sessionId));
        SessionZooKeeperClient sessionZKClient = createSessionZooKeeperClient(sessionIdPath);
        return new LocalSession(tenant, sessionId, sessionPreparer, applicationPackage, sessionZKClient,
                                getSessionAppDir(sessionId), applicationRepo, hostRegistry);
    }

    private long getActiveSessionId(ApplicationId applicationId) {
        List<ApplicationId> applicationIds = applicationRepo.activeApplications();
        if (applicationIds.contains(applicationId)) {
            return applicationRepo.requireActiveSessionOf(applicationId);
        }
        return nonExistingActiveSession;
    }

    long getNextSessionId() {
        return new SessionCounter(componentRegistry.getConfigCurator(), tenant).nextSessionId();
    }

    Path getSessionPath(long sessionId) {
        return sessionsPath.append(String.valueOf(sessionId));
    }

    private SessionZooKeeperClient createSessionZooKeeperClient(Path sessionPath) {
        return new SessionZooKeeperClient(curator, configCurator, sessionPath, serverId, nodeFlavors);
    }

    private File getAndValidateExistingSessionAppDir(long sessionId) {
        File appDir = getSessionAppDir(sessionId);
        if (!appDir.exists() || !appDir.isDirectory()) {
            throw new IllegalArgumentException("Unable to find correct application directory for session " + sessionId);
        }
        return appDir;
    }

    private File getSessionAppDir(long sessionId) {
        return new TenantFileSystemDirs(componentRegistry.getConfigServerDB(), tenant).getUserApplicationDir(sessionId);
    }

}
