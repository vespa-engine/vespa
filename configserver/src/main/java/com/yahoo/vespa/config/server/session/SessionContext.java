// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.vespa.config.server.host.HostValidator;
import com.yahoo.vespa.config.server.SuperModelGenerationCounter;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.TenantApplications;

import java.io.File;

/**
 * The dependencies needed for a local session to be edited and prepared.
 *
 * @author Ulf Lilleengen
 */
public class SessionContext {

    private final ApplicationPackage applicationPackage;
    private final SessionZooKeeperClient sessionZooKeeperClient;
    private final File serverDBSessionDir;
    private final TenantApplications applicationRepo;
    private final HostValidator<ApplicationId> hostRegistry;
    private final SuperModelGenerationCounter superModelGenerationCounter;

    public SessionContext(ApplicationPackage applicationPackage, SessionZooKeeperClient sessionZooKeeperClient,
                          File serverDBSessionDir, TenantApplications applicationRepo,
                          HostValidator<ApplicationId> hostRegistry, SuperModelGenerationCounter superModelGenerationCounter) {
        this.applicationPackage = applicationPackage;
        this.sessionZooKeeperClient = sessionZooKeeperClient;
        this.serverDBSessionDir = serverDBSessionDir;
        this.applicationRepo = applicationRepo;
        this.hostRegistry = hostRegistry;
        this.superModelGenerationCounter = superModelGenerationCounter;
    }

    public ApplicationPackage getApplicationPackage() {
        return applicationPackage;
    }

    public SessionZooKeeperClient getSessionZooKeeperClient() {
        return sessionZooKeeperClient;
    }

    public File getServerDBSessionDir() {
        return serverDBSessionDir;
    }

    public TenantApplications getApplicationRepo() {
        return applicationRepo;
    }
    
    public HostValidator<ApplicationId> getHostValidator() { return hostRegistry; }

    public SuperModelGenerationCounter getSuperModelGenerationCounter() {
        return superModelGenerationCounter;
    }

}
