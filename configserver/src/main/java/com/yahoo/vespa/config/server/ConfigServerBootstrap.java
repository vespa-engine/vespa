// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.TransientException;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.container.jdisc.state.StateMonitor;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.rpc.RpcServer;
import com.yahoo.vespa.config.server.version.VersionState;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
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
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.ConfigServerBootstrap.Mode.BOOTSTRAP_IN_CONSTRUCTOR;
import static com.yahoo.vespa.config.server.ConfigServerBootstrap.RedeployingApplicationsFails.CONTINUE;
import static com.yahoo.vespa.config.server.ConfigServerBootstrap.RedeployingApplicationsFails.EXIT_JVM;

/**
 * Main component that bootstraps and starts config server threads.
 *
 * If config server has been upgraded to a new version since the last time it was running it will redeploy all
 * applications. If that is done successfully the RPC server will start and the health status code will change from
 * 'initializing' to 'up'. If VIP status mode is VIP_STATUS_PROGRAMMATICALLY the config server
 * will be put into rotation (start serving status.html with 200 OK), if the mode is VIP_STATUS_FILE a VIP status
 * file is created or removed ny some external program based on the health status code.
 *
 * @author Ulf Lilleengen
 * @author hmusum
 */
public class ConfigServerBootstrap extends AbstractComponent implements Runnable {

    private static final Logger log = Logger.getLogger(ConfigServerBootstrap.class.getName());

    // INITIALIZE_ONLY is for testing only
    enum Mode {BOOTSTRAP_IN_CONSTRUCTOR, BOOTSTRAP_IN_SEPARATE_THREAD, INITIALIZE_ONLY}
    enum RedeployingApplicationsFails {EXIT_JVM, CONTINUE}
    enum VipStatusMode {VIP_STATUS_FILE, VIP_STATUS_PROGRAMMATICALLY}

    private final ApplicationRepository applicationRepository;
    private final RpcServer server;
    private final Optional<Thread> serverThread;
    private final VersionState versionState;
    private final StateMonitor stateMonitor;
    private final VipStatus vipStatus;
    private final ConfigserverConfig configserverConfig;
    private final SuperModelManager superModelManager;
    private final Duration maxDurationOfRedeployment;
    private final Duration sleepTimeWhenRedeployingFails;
    private final RedeployingApplicationsFails exitIfRedeployingApplicationsFails;
    private final ExecutorService rpcServerExecutor;

    @SuppressWarnings("unused")
    @Inject
    public ConfigServerBootstrap(ApplicationRepository applicationRepository, RpcServer server,
                                 VersionState versionState, StateMonitor stateMonitor, VipStatus vipStatus,
                                 SuperModelManager superModelManager) {
        this(applicationRepository, server, versionState, stateMonitor, vipStatus, BOOTSTRAP_IN_CONSTRUCTOR, EXIT_JVM,
             applicationRepository.configserverConfig().hostedVespa()
                     ? VipStatusMode.VIP_STATUS_FILE
                     : VipStatusMode.VIP_STATUS_PROGRAMMATICALLY,
                superModelManager);
    }

    // For testing only
    ConfigServerBootstrap(ApplicationRepository applicationRepository, RpcServer server, VersionState versionState,
                          StateMonitor stateMonitor, VipStatus vipStatus, Mode mode,  VipStatusMode vipStatusMode) {
        this(applicationRepository, server, versionState, stateMonitor, vipStatus, mode, CONTINUE, vipStatusMode, null);
    }

    private ConfigServerBootstrap(ApplicationRepository applicationRepository, RpcServer server,
                                  VersionState versionState, StateMonitor stateMonitor, VipStatus vipStatus,
                                  Mode mode, RedeployingApplicationsFails exitIfRedeployingApplicationsFails,
                                  VipStatusMode vipStatusMode, SuperModelManager superModelManager) {
        this.applicationRepository = applicationRepository;
        this.server = server;
        this.versionState = versionState;
        this.stateMonitor = stateMonitor;
        this.vipStatus = vipStatus;
        this.configserverConfig = applicationRepository.configserverConfig();
        this.superModelManager = superModelManager;
        this.maxDurationOfRedeployment = Duration.ofSeconds(configserverConfig.maxDurationOfBootstrap());
        this.sleepTimeWhenRedeployingFails = Duration.ofSeconds(configserverConfig.sleepTimeWhenRedeployingFails());
        this.exitIfRedeployingApplicationsFails = exitIfRedeployingApplicationsFails;
        rpcServerExecutor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("config server RPC server"));
        log.log(LogLevel.DEBUG, "Bootstrap mode: " + mode + ", VIP status mode: " + vipStatusMode);
        initializing(vipStatusMode);
        switch (mode) {
            case BOOTSTRAP_IN_SEPARATE_THREAD:
                this.serverThread = Optional.of(new Thread(this, "config server bootstrap thread"));
                serverThread.get().start();
                break;
            case BOOTSTRAP_IN_CONSTRUCTOR:
                this.serverThread = Optional.empty();
                start();
                break;
            case INITIALIZE_ONLY:
                this.serverThread = Optional.empty();
                break;
            default:
                throw new IllegalArgumentException("Unknown bootstrap mode " + mode + ", legal values: " + Arrays.toString(Mode.values()));
        }
    }

    @Override
    public void deconstruct() {
        log.log(LogLevel.INFO, "Stopping config server");
        down();
        server.stop();
        log.log(LogLevel.DEBUG, "RPC server stopped");
        rpcServerExecutor.shutdown();
        serverThread.ifPresent(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                log.log(LogLevel.WARNING, "Error joining server thread on shutdown: " + e.getMessage());
            }
        });
    }

    @Override
    public void run() {
        start();
        do {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.log(LogLevel.ERROR, "Got interrupted", e);
                break;
            }
        } while (server.isRunning());
        down();
    }

    public void start() {
        if (versionState.isUpgraded()) {
            log.log(LogLevel.INFO, "Config server upgrading from " + versionState.storedVersion() + " to "
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
    }

    StateMonitor.Status status() {
        return stateMonitor.status();
    }

    private void up() {
        vipStatus.setInRotation(true);
    }

    private void down() {
        vipStatus.setInRotation(false);
    }

    private void initializing(VipStatusMode vipStatusMode) {
        stateMonitor.status(StateMonitor.Status.initializing);
        if (vipStatusMode == VipStatusMode.VIP_STATUS_PROGRAMMATICALLY)
            vipStatus.setInRotation(false);
    }

    private void startRpcServer() {
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
    }

    private void redeployingApplicationsFailed() {
        if (exitIfRedeployingApplicationsFails == EXIT_JVM) System.exit(1);
    }

    private boolean redeployAllApplications() throws InterruptedException {
        Instant end = Instant.now().plus(maxDurationOfRedeployment);
        Set<ApplicationId> applicationsNotRedeployed = applicationRepository.listApplications();
        do {
            applicationsNotRedeployed = redeployApplications(applicationsNotRedeployed);
            if ( ! applicationsNotRedeployed.isEmpty()) {
                log.log(LogLevel.INFO, "Redeployment of " + applicationsNotRedeployed +
                        " failed, will retry in " + sleepTimeWhenRedeployingFails);
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
            if (deploymentOptional.isEmpty()) continue;

            futures.put(appId, executor.submit(deploymentOptional.get()::activate));
        }

        for (Map.Entry<ApplicationId, Future<?>> f : futures.entrySet()) {
            ApplicationId app = f.getKey();
            try {
                f.getValue().get();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof TransientException) {
                    log.log(LogLevel.INFO, "Redeploying " + app +
                            " failed with transient error, will retry after bootstrap: " + Exceptions.toMessageString(e));
                } else {
                    log.log(LogLevel.WARNING, "Redeploying " + app + " failed, will retry", e);
                    failedDeployments.add(app);
                }
            }
        }
        executor.shutdown();
        executor.awaitTermination(365, TimeUnit.DAYS); // Timeout should never happen
        return failedDeployments;
    }

}

