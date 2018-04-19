// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.application.provider.*;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.*;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs;
import com.yahoo.vespa.config.server.host.HostValidator;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.Tenants;
import com.yahoo.vespa.config.server.zookeeper.SessionCounter;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.curator.Curator;

import java.io.File;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Serves as the factory of sessions. Takes care of copying files to the correct folder and initializing the
 * session state.
 *
 * @author Ulf Lilleengen
 */
public class SessionFactoryImpl implements SessionFactory, LocalSessionLoader {

    private static final Logger log = Logger.getLogger(SessionFactoryImpl.class.getName());
    private static final long nonExistingActiveSession = 0;

    private final SessionPreparer sessionPreparer;
    private final Curator curator;
    private final ConfigCurator configCurator;
    private final SessionCounter sessionCounter;
    private final TenantApplications applicationRepo;
    private final Path sessionsPath;
    private final TenantFileSystemDirs tenantFileSystemDirs;
    private final HostValidator<ApplicationId> hostRegistry;
    private final SuperModelGenerationCounter superModelGenerationCounter;
    private final ConfigDefinitionRepo defRepo;
    private final TenantName tenant;
    private final String serverId;
    private final Optional<NodeFlavors> nodeFlavors;
    private final Clock clock;

    public SessionFactoryImpl(GlobalComponentRegistry globalComponentRegistry,
                              SessionCounter sessionCounter,
                              TenantApplications applicationRepo,
                              TenantFileSystemDirs tenantFileSystemDirs,
                              HostValidator<ApplicationId> hostRegistry,
                              TenantName tenant) {
        this.hostRegistry = hostRegistry;
        this.tenant = tenant;
        this.sessionPreparer = globalComponentRegistry.getSessionPreparer();
        this.curator = globalComponentRegistry.getCurator();
        this.configCurator = globalComponentRegistry.getConfigCurator();
        this.sessionCounter = sessionCounter;
        this.sessionsPath = Tenants.getSessionsPath(tenant);
        this.applicationRepo = applicationRepo;
        this.tenantFileSystemDirs = tenantFileSystemDirs;
        this.superModelGenerationCounter = globalComponentRegistry.getSuperModelGenerationCounter();
        this.defRepo = globalComponentRegistry.getConfigDefinitionRepo();
        this.serverId = globalComponentRegistry.getConfigserverConfig().serverId();
        this.nodeFlavors = globalComponentRegistry.getZone().nodeFlavors();
        this.clock = globalComponentRegistry.getClock();
    }

    @Override
    public LocalSession createSession(File applicationFile, String applicationName, TimeoutBudget timeoutBudget) {
        return create(applicationFile, applicationName, nonExistingActiveSession, timeoutBudget);
    }

    private void ensureZKPathDoesNotExist(Path sessionPath) {
        if (configCurator.exists(sessionPath.getAbsolute())) {
            throw new IllegalArgumentException("Path " + sessionPath.getAbsolute() + " already exists in ZooKeeper");
        }
    }

    private ApplicationPackage createApplication(File userDir,
                                                 File configApplicationDir,
                                                 String applicationName,
                                                 long sessionId,
                                                 long currentlyActiveSession) {
        long deployTimestamp = System.currentTimeMillis();
        String user = System.getenv("USER");
        if (user == null) {
            user = "unknown";
        }
        DeployData deployData = new DeployData(user, userDir.getAbsolutePath(), applicationName, deployTimestamp, sessionId, currentlyActiveSession);
        return FilesApplicationPackage.fromFileWithDeployData(configApplicationDir, deployData);
    }

    private LocalSession createSessionFromApplication(ApplicationPackage applicationPackage,
                                                      long sessionId,
                                                      SessionZooKeeperClient sessionZKClient,
                                                      TimeoutBudget timeoutBudget,
                                                      Clock clock) {
        log.log(LogLevel.DEBUG, Tenants.logPre(tenant) + "Creating session " + sessionId + " in ZooKeeper");
        sessionZKClient.createNewSession(clock.instant().toEpochMilli(), TimeUnit.MILLISECONDS);
        log.log(LogLevel.DEBUG, Tenants.logPre(tenant) + "Creating upload waiter for session " + sessionId);
        Curator.CompletionWaiter waiter = sessionZKClient.getUploadWaiter();
        log.log(LogLevel.DEBUG, Tenants.logPre(tenant) + "Done creating upload waiter for session " + sessionId);
        LocalSession session = new LocalSession(tenant, sessionId, sessionPreparer, new SessionContext(applicationPackage, sessionZKClient, getSessionAppDir(sessionId), applicationRepo, hostRegistry, superModelGenerationCounter));
        log.log(LogLevel.DEBUG, Tenants.logPre(tenant) + "Waiting on upload waiter for session " + sessionId);
        waiter.awaitCompletion(timeoutBudget.timeLeft());
        log.log(LogLevel.DEBUG, Tenants.logPre(tenant) + "Done waiting on upload waiter for session " + sessionId);
        return session;
    }

    @Override
    public LocalSession createSessionFromExisting(LocalSession existingSession,
                                                  DeployLogger logger,
                                                  TimeoutBudget timeoutBudget) {
        File existingApp = getSessionAppDir(existingSession.getSessionId());
        ApplicationMetaData metaData = FilesApplicationPackage.readMetaData(existingApp);
        ApplicationId existingApplicationId = existingSession.getApplicationId();

        long liveApp = getLiveApp(existingApplicationId);
        logger.log(LogLevel.DEBUG, "Create from existing application id " + existingApplicationId + ", live app for it is " + liveApp);
        LocalSession session = create(existingApp, metaData.getApplicationName(), liveApp, timeoutBudget);
        session.setApplicationId(existingApplicationId);
        session.setVespaVersion(existingSession.getVespaVersion());
        return session;
    }

    private LocalSession create(File applicationFile, String applicationName, long currentlyActiveSession, TimeoutBudget timeoutBudget) {
        long sessionId = sessionCounter.nextSessionId();
        Path sessionIdPath = sessionsPath.append(String.valueOf(sessionId));
        log.log(LogLevel.DEBUG, Tenants.logPre(tenant) + "Next session id is " + sessionId + " , sessionIdPath=" + sessionIdPath.getAbsolute());
        try {
            ensureZKPathDoesNotExist(sessionIdPath);
            SessionZooKeeperClient sessionZooKeeperClient = new SessionZooKeeperClient(curator,
                                                                                       configCurator,
                                                                                       sessionIdPath,
                                                                                       defRepo,
                                                                                       serverId,
                                                                                       nodeFlavors);
            File userApplicationDir = tenantFileSystemDirs.getUserApplicationDir(sessionId);
            IOUtils.copyDirectory(applicationFile, userApplicationDir);
            ApplicationPackage applicationPackage = createApplication(applicationFile, userApplicationDir, applicationName, sessionId, currentlyActiveSession);
            applicationPackage.writeMetaData();
            return createSessionFromApplication(applicationPackage, sessionId, sessionZooKeeperClient, timeoutBudget, clock);
        } catch (Exception e) {
            throw new RuntimeException("Error creating session " + sessionIdPath, e);
        }
    }

    private File getSessionAppDir(long sessionId) {
        File appDir = tenantFileSystemDirs.getUserApplicationDir(sessionId);
        if (!appDir.exists() || !appDir.isDirectory()) {
            throw new IllegalArgumentException("Unable to find correct application directory for session " + sessionId);
        }
        return appDir;
    }

    @Override
    public LocalSession loadSession(long sessionId) {
        File sessionDir = getSessionAppDir(sessionId);
        ApplicationPackage applicationPackage = FilesApplicationPackage.fromFile(sessionDir);
        Path sessionIdPath = sessionsPath.append(String.valueOf(sessionId));
        SessionZooKeeperClient sessionZKClient = new SessionZooKeeperClient(curator,
                                                                            configCurator,
                                                                            sessionIdPath,
                                                                            defRepo,
                                                                            serverId,
                                                                            nodeFlavors);
        SessionContext context = new SessionContext(applicationPackage, sessionZKClient, sessionDir, applicationRepo, 
                                                    hostRegistry, superModelGenerationCounter);
        return new LocalSession(tenant, sessionId, sessionPreparer, context);
    }

    private long getLiveApp(ApplicationId applicationId) {
        List<ApplicationId> applicationIds = applicationRepo.listApplications();
        if (applicationIds.contains(applicationId)) {
            return applicationRepo.getSessionIdForApplication(applicationId);
        }
        return nonExistingActiveSession;
    }
}
