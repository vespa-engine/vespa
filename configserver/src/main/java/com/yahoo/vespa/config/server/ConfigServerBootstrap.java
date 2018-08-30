// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployment;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.container.jdisc.state.StateMonitor;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.rpc.RpcServer;
import com.yahoo.vespa.config.server.version.VersionState;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Main component that bootstraps and starts config server threads.
 *
 * If config server has been upgraded to a new version since the last time it was running it will redeploy all
 * applications. If that is done successfully the RPC server will start and the health status code will change from
 * 'initializing' to 'up' and the config server will be put into rotation (start serving status.html with 200 OK)
 *
 * @author Ulf Lilleengen
 * @author hmusum
 */
public class ConfigServerBootstrap extends AbstractComponent implements Runnable {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(ConfigServerBootstrap.class.getName());
    private static final String vipStatusClusterIdentifier = "configserver";

    enum MainThread {START, DO_NOT_START}
    enum RedeployingApplicationsFails {EXIT_JVM, CONTINUE}

    private final ApplicationRepository applicationRepository;
    private final RpcServer server;
    private final Thread serverThread;
    private final VersionState versionState;
    private final StateMonitor stateMonitor;
    private final VipStatus vipStatus;
    private final ConfigserverConfig configserverConfig;
    private final Duration maxDurationOfRedeployment;
    private final Duration sleepTimeWhenRedeployingFails;
    private final RedeployingApplicationsFails exitIfRedeployingApplicationsFails;
    private final ExecutorService rpcServerExecutor;

    // The tenants object is injected so that all initial requests handlers are
    // added to the rpc server before it starts answering rpc requests.
    @SuppressWarnings("WeakerAccess")
    @Inject
    public ConfigServerBootstrap(ApplicationRepository applicationRepository, RpcServer server,
                                 VersionState versionState, StateMonitor stateMonitor, VipStatus vipStatus) {
        this(applicationRepository, server, versionState, stateMonitor, vipStatus, MainThread.START, RedeployingApplicationsFails.EXIT_JVM);
    }

    // For testing only
    ConfigServerBootstrap(ApplicationRepository applicationRepository, RpcServer server, VersionState versionState,
                          StateMonitor stateMonitor, VipStatus vipStatus, MainThread mainThread,
                          RedeployingApplicationsFails exitIfRedeployingApplicationsFails) {
        this.applicationRepository = applicationRepository;
        this.server = server;
        this.versionState = versionState;
        this.stateMonitor = stateMonitor;
        this.serverThread = new Thread(this, "configserver main");
        this.vipStatus = vipStatus;
        this.configserverConfig = applicationRepository.configserverConfig();
        this.maxDurationOfRedeployment = Duration.ofSeconds(configserverConfig.maxDurationOfBootstrap());
        this.sleepTimeWhenRedeployingFails = Duration.ofSeconds(configserverConfig.sleepTimeWhenRedeployingFails());
        this.exitIfRedeployingApplicationsFails = exitIfRedeployingApplicationsFails;
        rpcServerExecutor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("config server RPC server"));
        initializing(); // Initially take server out of rotation
        if (mainThread == MainThread.START)
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
                if ( ! redeployAllApplications()) {
                    redeployingApplicationsFailed();
                    return; // Status will not be set to 'up' since we return here
                }
                versionState.saveNewVersion();
                log.log(LogLevel.INFO, "All applications redeployed successfully");
            } catch (Exception e) {
                log.log(LogLevel.ERROR, "Redeployment of applications failed", e);
                redeployingApplicationsFailed();
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

    private void redeployingApplicationsFailed() {
        if (exitIfRedeployingApplicationsFails == RedeployingApplicationsFails.EXIT_JVM) System.exit(1);
    }

    private boolean redeployAllApplications() throws InterruptedException {
        Instant end = Instant.now().plus(maxDurationOfRedeployment);
        Set<ApplicationId> applicationsNotRedeployed = applicationRepository.listApplications();
        do {
            applicationsNotRedeployed = redeployApplications(applicationsNotRedeployed);
            if ( ! applicationsNotRedeployed.isEmpty()) {
                Thread.sleep(sleepTimeWhenRedeployingFails.toMillis());
            }
        } while ( ! applicationsNotRedeployed.isEmpty() && Instant.now().isBefore(end));

        if ( ! applicationsNotRedeployed.isEmpty()) {
            log.log(LogLevel.ERROR, "Redeploying applications not finished after " + maxDurationOfRedeployment +
                    ", exiting, applications that failed redeployment: " + applicationsNotRedeployed);
            return false;
        }
        return true;
    }

    // Returns the set of applications that failed to redeploy
    private Set<ApplicationId> redeployApplications(Set<ApplicationId> applicationIds) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(configserverConfig.numParallelTenantLoaders(),
                                                                new DaemonThreadFactory("redeploy apps"));
        // Keep track of deployment per application
        Map<ApplicationId, Future<?>> futures = new HashMap<>();
        Set<ApplicationId> failedDeployments = new HashSet<>();

        for (ApplicationId appId : applicationIds) {
            Optional<Deployment> deploymentOptional = applicationRepository.deployFromLocalActive(appId, true /* bootstrap */);
            if ( ! deploymentOptional.isPresent()) continue;

            futures.put(appId, executor.submit(deploymentOptional.get()::activate));
        }

        for (Map.Entry<ApplicationId, Future<?>> f : futures.entrySet()) {
            try {
                f.getValue().get();
            } catch (ExecutionException e) {
                ApplicationId app = f.getKey();
                log.log(LogLevel.WARNING, "Redeploying " + app + " failed, will retry", e);
                failedDeployments.add(app);
            }
        }
        executor.shutdown();
        executor.awaitTermination(365, TimeUnit.DAYS); // Timeout should never happen
        return failedDeployments;
    }

}

