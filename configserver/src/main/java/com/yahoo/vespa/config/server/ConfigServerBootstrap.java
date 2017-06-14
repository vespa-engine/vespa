// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.Deployer;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.rpc.RpcServer;
import com.yahoo.vespa.config.server.version.VersionState;

/**
 * Main component that bootstraps and starts config server threads.
 *
 * @author lulf
 * @since 5.1
 */
public class ConfigServerBootstrap extends AbstractComponent implements Runnable {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(ConfigServerBootstrap.class.getName());

    private final ApplicationRepository applicationRepository;
    private final RpcServer server;
    private final Thread serverThread;
    private final Deployer deployer;
    private final VersionState versionState;

    // The tenants object is injected so that all initial requests handlers are
    // added to the rpc server before it starts answering rpc requests.
    @SuppressWarnings("UnusedParameters")
    @Inject
    public ConfigServerBootstrap(ApplicationRepository applicationRepository, RpcServer server,
                                 Deployer deployer, VersionState versionState) {
        this.applicationRepository = applicationRepository;
        this.server = server;
        this.deployer = deployer;
        this.versionState = versionState;
        this.serverThread = new Thread(this, "configserver main");
        serverThread.start();
    }

    @Override
    public void deconstruct() {
        log.log(LogLevel.INFO, "Stopping config server");
        server.stop();
        try {
            serverThread.join();
        } catch (InterruptedException e) {
            log.log(LogLevel.WARNING, "Error joining server thread on shutdown: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        if (versionState.isUpgraded()) {
            log.log(LogLevel.INFO, "Configserver upgraded from " + versionState.storedVersion() + " to "
                    + versionState.currentVersion() + ". Redeploying all applications");
            try {
                applicationRepository.redeployAllApplications(deployer);
            } catch (InterruptedException e) {
                throw new RuntimeException("Redeploying applications failed", e);
            }
            log.log(LogLevel.INFO, "All applications redeployed");
        }
        versionState.saveNewVersion();
        log.log(LogLevel.DEBUG, "Starting RPC server");
        server.run();
        log.log(LogLevel.DEBUG, "RPC server stopped");
    }

}

