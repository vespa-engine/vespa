// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.jdisc.state.StateMonitor;
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
    private final VersionState versionState;
    private final StateMonitor stateMonitor;

    // The tenants object is injected so that all initial requests handlers are
    // added to the rpc server before it starts answering rpc requests.
    @SuppressWarnings("WeakerAccess")
    @Inject
    public ConfigServerBootstrap(ApplicationRepository applicationRepository, RpcServer server,
                                 VersionState versionState, StateMonitor stateMonitor) {
        this(applicationRepository, server, versionState, stateMonitor, true);
    }

    // For testing only
    ConfigServerBootstrap(ApplicationRepository applicationRepository, RpcServer server,
                          VersionState versionState, StateMonitor stateMonitor, boolean startMainThread) {
        this.applicationRepository = applicationRepository;
        this.server = server;
        this.versionState = versionState;
        this.stateMonitor = stateMonitor;
        this.serverThread = new Thread(this, "configserver main");
        if (startMainThread)
            start();
    }

    private void start() {
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
            log.log(LogLevel.INFO, "Configserver upgrading from " + versionState.storedVersion() + " to "
                    + versionState.currentVersion() + ". Redeploying all applications");
            try {
                applicationRepository.redeployAllApplications();
                versionState.saveNewVersion();
                log.log(LogLevel.INFO, "All applications redeployed successfully");
            } catch (Exception e) {
                log.log(LogLevel.ERROR, "Redeployment of applications failed", e);
                return; // Status will not be set to 'up' since we return here
            }
        }
        stateMonitor.status(StateMonitor.Status.up);
        log.log(LogLevel.INFO, "Starting RPC server");
        server.run();
        do {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.log(LogLevel.ERROR, "Got interrupted", e);
                break;
            }
        } while (server.isRunning());
        log.log(LogLevel.INFO, "RPC server stopped");
        stateMonitor.status(StateMonitor.Status.down);
    }

    StateMonitor.Status status() {
        return stateMonitor.status();
    }

}

