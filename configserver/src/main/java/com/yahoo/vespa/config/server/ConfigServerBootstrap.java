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
import com.yahoo.vespa.config.server.rpc.RpcServer;
import com.yahoo.vespa.config.server.version.VersionState;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
 * file is created or removed by some external program based on the health status code.
 *
 * @author Ulf Lilleengen
 * @author hmusum
 */
public class ConfigServerBootstrap extends AbstractComponent implements Runnable {

    private static final Logger log = Logger.getLogger(ConfigServerBootstrap.class.getName());

    // INITIALIZE_ONLY is for testing only
    enum Mode { BOOTSTRAP_IN_CONSTRUCTOR, BOOTSTRAP_IN_SEPARATE_THREAD, INITIALIZE_ONLY }
    enum RedeployingApplicationsFails { EXIT_JVM, CONTINUE }
    enum VipStatusMode { VIP_STATUS_FILE, VIP_STATUS_PROGRAMMATICALLY }

    private final ApplicationRepository applicationRepository;
    private final RpcServer server;
    private final VersionState versionState;
    private final StateMonitor stateMonitor;
    private final VipStatus vipStatus;
    private final ConfigserverConfig configserverConfig;
    private final Duration maxDurationOfRedeployment;
    private final Duration sleepTimeWhenRedeployingFails;
    private final RedeployingApplicationsFails exitIfRedeployingApplicationsFails;
    private final ExecutorService rpcServerExecutor;
    private final Optional<ExecutorService> bootstrapExecutor;

    @Inject
    public ConfigServerBootstrap(ApplicationRepository applicationRepository, RpcServer server,
                                 VersionState versionState, StateMonitor stateMonitor, VipStatus vipStatus) {
        this(applicationRepository, server, versionState, stateMonitor, vipStatus, BOOTSTRAP_IN_CONSTRUCTOR, EXIT_JVM,
             applicationRepository.configserverConfig().hostedVespa()
                     ? VipStatusMode.VIP_STATUS_FILE
                     : VipStatusMode.VIP_STATUS_PROGRAMMATICALLY);
    }

    // For testing only
    ConfigServerBootstrap(ApplicationRepository applicationRepository, RpcServer server, VersionState versionState,
                          StateMonitor stateMonitor, VipStatus vipStatus, Mode mode, VipStatusMode vipStatusMode) {
        this(applicationRepository, server, versionState, stateMonitor, vipStatus, mode, CONTINUE, vipStatusMode);
    }

    private ConfigServerBootstrap(ApplicationRepository applicationRepository, RpcServer server,
                                  VersionState versionState, StateMonitor stateMonitor, VipStatus vipStatus,
                                  Mode mode, RedeployingApplicationsFails exitIfRedeployingApplicationsFails,
                                  VipStatusMode vipStatusMode) {
        this.applicationRepository = applicationRepository;
        this.server = server;
        this.versionState = versionState;
        this.stateMonitor = stateMonitor;
        this.vipStatus = vipStatus;
        this.configserverConfig = applicationRepository.configserverConfig();
        this.maxDurationOfRedeployment = Duration.ofSeconds(configserverConfig.maxDurationOfBootstrap());
        this.sleepTimeWhenRedeployingFails = Duration.ofSeconds(configserverConfig.sleepTimeWhenRedeployingFails());
        this.exitIfRedeployingApplicationsFails = exitIfRedeployingApplicationsFails;
        rpcServerExecutor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("config server RPC server"));

        log.log(Level.FINE, "Bootstrap mode: " + mode + ", VIP status mode: " + vipStatusMode);
        initializing(vipStatusMode);

        switch (mode) {
            case BOOTSTRAP_IN_SEPARATE_THREAD:
                bootstrapExecutor = Optional.of(Executors.newSingleThreadExecutor(new DaemonThreadFactory("config server bootstrap")));
                bootstrapExecutor.get().execute(this);
                break;
            case BOOTSTRAP_IN_CONSTRUCTOR:
                bootstrapExecutor = Optional.empty();
                start();
                break;
            case INITIALIZE_ONLY:
                bootstrapExecutor = Optional.empty();
                break;
            default:
                throw new IllegalArgumentException("Unknown bootstrap mode " + mode + ", legal values: " + Arrays.toString(Mode.values()));
        }
    }

    @Override
    public void deconstruct() {
        log.log(Level.INFO, "Stopping config server");
        down();
        server.stop();
        log.log(Level.FINE, "RPC server stopped");
        rpcServerExecutor.shutdown();
        bootstrapExecutor.ifPresent(ExecutorService::shutdownNow);
    }

    @Override
    public void run() {
        start();
        do {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.log(Level.SEVERE, "Got interrupted", e);
                break;
            }
        } while (server.isRunning());
        down();
    }

    public void start() {
        if (versionState.isUpgraded()) {
            log.log(Level.INFO, "Config server upgrading from " + versionState.storedVersion() + " to "
                    + versionState.currentVersion() + ". Redeploying all applications");
            try {
                if ( ! redeployAllApplications()) {
                    redeployingApplicationsFailed();
                    return; // Status will not be set to 'up' since we return here
                }
                versionState.saveNewVersion();
                log.log(Level.INFO, "All applications redeployed successfully");
            } catch (Exception e) {
                log.log(Level.SEVERE, "Redeployment of applications failed", e);
                redeployingApplicationsFailed();
                return; // Status will not be set to 'up' since we return here
            }
        }
        applicationRepository.bootstrappingDone();
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
                log.log(Level.SEVERE, "Got interrupted", e);
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
        List<ApplicationId> applicationsNotRedeployed = applicationRepository.listApplications();
        Collections.shuffle(applicationsNotRedeployed);
        long failCount = 0;
        do {
            applicationsNotRedeployed = redeployApplications(applicationsNotRedeployed);
            if ( ! applicationsNotRedeployed.isEmpty() && ! sleepTimeWhenRedeployingFails.isZero()) {
                Duration sleepTime = sleepTimeWhenRedeployingFails.multipliedBy(++failCount);
                if (sleepTime.compareTo(Duration.ofMinutes(10)) > 0)
                    sleepTime = Duration.ofMinutes(10);
                log.log(Level.INFO, "Redeployment of " + applicationsNotRedeployed + " not finished, will retry in " + sleepTime);
                Thread.sleep(sleepTime.toMillis());
            }
        } while ( ! applicationsNotRedeployed.isEmpty() && Instant.now().isBefore(end));

        if ( ! applicationsNotRedeployed.isEmpty()) {
            log.log(Level.SEVERE, "Redeploying applications not finished after " + maxDurationOfRedeployment +
                    ", exiting, applications that failed redeployment: " + applicationsNotRedeployed);
            return false;
        }
        return true;
    }

    // Returns the set of applications that failed to redeploy
    private List<ApplicationId> redeployApplications(List<ApplicationId> applicationIds) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(configserverConfig.numParallelTenantLoaders(),
                                                                new DaemonThreadFactory("redeploy apps"));
        // Keep track of deployment per application
        Map<ApplicationId, Future<?>> deployments = new HashMap<>();
        log.log(Level.INFO, () -> "Redeploying " + applicationIds);
        applicationIds.forEach(appId -> deployments.put(appId,
                                                    executor.submit(() -> applicationRepository.deployFromLocalActive(appId, true /* bootstrap */)
                                .ifPresent(Deployment::activate))));

        List<ApplicationId> failedDeployments =
                deployments.entrySet().stream()
                        .map(entry -> checkDeployment(entry.getKey(), entry.getValue()))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList());

        executor.shutdown();
        executor.awaitTermination(365, TimeUnit.DAYS); // Timeout should never happen
        return failedDeployments;
    }

    // Returns an application id if deployment failed
    private Optional<ApplicationId> checkDeployment(ApplicationId applicationId, Future<?> future) {
        try {
            future.get();
            log.log(Level.INFO, () -> applicationId + " redeployed");
        } catch (ExecutionException | InterruptedException e) {
            if (e.getCause() instanceof TransientException) {
                log.log(Level.INFO, "Redeploying " + applicationId +
                                    " failed with transient error, will retry after bootstrap: " + Exceptions.toMessageString(e));
            } else {
                log.log(Level.WARNING, "Redeploying " + applicationId + " failed, will retry", e);
                return Optional.of(applicationId);
            }
        }
        return Optional.empty();
    }

}

