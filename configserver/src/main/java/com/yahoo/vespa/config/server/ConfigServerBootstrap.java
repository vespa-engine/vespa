// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.container.jdisc.state.StateMonitor;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.rpc.RpcServer;
import com.yahoo.vespa.config.server.version.VersionState;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main component that bootstraps and starts config server threads.
 *
 * If config server has been upgraded to a new version since the last time it was running it will redeploy all
 * applications. If that is done syúccessfully the RPC server will start and the health status code will change from
 * 'initializing' to 'up' and the config server will be put into rotation (start serving status.html with 200 OK)
 *
 * @author Ulf Lilleengen
 * @author hmusum
 */
public class ConfigServerBootstrap extends AbstractComponent implements Runnable {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(ConfigServerBootstrap.class.getName());
    private static final ExecutorService rpcServerExecutor  = Executors.newSingleThreadExecutor(new DaemonThreadFactory("config server RPC server"));
    private static final String vipStatusClusterIdentifier = "configserver";

    private final ApplicationRepository applicationRepository;
    private final RpcServer server;
    private final Thread serverThread;
    private final VersionState versionState;
    private final StateMonitor stateMonitor;
    private final VipStatus vipStatus;

    // The tenants object is injected so that all initial requests handlers are
    // added to the rpc server before it starts answering rpc requests.
    @SuppressWarnings("WeakerAccess")
    @Inject
    public ConfigServerBootstrap(ApplicationRepository applicationRepository, RpcServer server,
                                 VersionState versionState, StateMonitor stateMonitor, VipStatus vipStatus) {
        this(applicationRepository, server, versionState, stateMonitor, vipStatus, true);
    }

    // For testing only
    ConfigServerBootstrap(ApplicationRepository applicationRepository, RpcServer server, VersionState versionState,
                          StateMonitor stateMonitor, VipStatus vipStatus, boolean startMainThread) {
        this.applicationRepository = applicationRepository;
        this.server = server;
        this.versionState = versionState;
        this.stateMonitor = stateMonitor;
        this.serverThread = new Thread(this, "configserver main");
        this.vipStatus = vipStatus;
        initializing(); // Initially take server out of rotation
        if (startMainThread)
            start();
    }

    @Override
    public void deconstruct() {
        log.log(LogLevel.INFO, "Stopping config server");
        down();
        server.stop();
        rpcServerExecutor.shutdown();
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
        startRpcServer();
        up();
        do {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.log(LogLevel.ERROR, "Got interrupted", e);
                break;
            }
        } while (server.isRunning());
        down();
        log.log(LogLevel.INFO, "RPC server stopped");
    }

    StateMonitor.Status status() {
        return stateMonitor.status();
    }

    private void start() {
        serverThread.start();
    }

    private void up() {
        stateMonitor.status(StateMonitor.Status.up);
        vipStatus.addToRotation(vipStatusClusterIdentifier);
    }

    private void down() {
        stateMonitor.status(StateMonitor.Status.down);
        vipStatus.removeFromRotation(vipStatusClusterIdentifier);
    }

    private void initializing() {
        // This is default value (from config), so not strictly necessary
        stateMonitor.status(StateMonitor.Status.initializing);
        vipStatus.removeFromRotation(vipStatusClusterIdentifier);
    }

    private void startRpcServer() {
        log.log(LogLevel.INFO, "Starting RPC server");
        rpcServerExecutor.execute(server);

        Instant end = Instant.now().plus(Duration.ofSeconds(10));
        while (!server.isRunning() && Instant.now().isBefore(end)) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                log.log(LogLevel.ERROR, "Got interrupted", e);
                break;
            }
        }
        if (!server.isRunning())
            throw new RuntimeException("RPC server not started in 10 seconds");
        log.log(LogLevel.INFO, "RPC server started");
    }

}

