// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.TransientException;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.container.jdisc.state.StateMonitor;
import com.yahoo.vespa.config.server.filedistribution.FileDirectory;
import com.yahoo.vespa.config.server.maintenance.ConfigServerMaintenance;
import com.yahoo.vespa.config.server.rpc.RpcServer;
import com.yahoo.vespa.config.server.version.VersionState;
import com.yahoo.yolean.Exceptions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.ConfigServerBootstrap.RedeployingApplicationsFails.EXIT_JVM;

/**
 * Main component that bootstraps and starts config server threads.
 *
 * Starts RPC server without allowing config requests. If config server has been upgraded to a new version since the
 * last time it was running it will redeploy all applications. If that is done successfully the RPC server will start
 * allowing config requests and the health status code will change from 'initializing' to 'up'. If VIP status mode is
 * VIP_STATUS_PROGRAMMATICALLY the config server will be put into rotation (start serving status.html with 200 OK),
 * if the mode is VIP_STATUS_FILE a VIP status file is created or removed by some external program based on the
 * health status code.
 *
 * @author Ulf Lilleengen
 * @author hmusum
 */
public class ConfigServerBootstrap extends AbstractComponent implements Runnable {

    private static final Logger log = Logger.getLogger(ConfigServerBootstrap.class.getName());

    enum RedeployingApplicationsFails { EXIT_JVM, CONTINUE }
    enum VipStatusMode { VIP_STATUS_FILE, VIP_STATUS_PROGRAMMATICALLY }

    private final ApplicationRepository applicationRepository;
    private final RpcServer server;
    protected final VersionState versionState;
    private final StateMonitor stateMonitor;
    private final VipStatus vipStatus;
    private final ConfigserverConfig configserverConfig;
    private final Duration maxDurationOfRedeployment;
    private final Duration sleepTimeWhenRedeployingFails;
    private final RedeployingApplicationsFails exitIfRedeployingApplicationsFails;
    private final ExecutorService rpcServerExecutor;
    private final ConfigServerMaintenance configServerMaintenance;
    private final Clock clock;

    @SuppressWarnings("unused") //  Injected component
    @Inject
    public ConfigServerBootstrap(ApplicationRepository applicationRepository, RpcServer server,
                                 VersionState versionState, StateMonitor stateMonitor, VipStatus vipStatus,
                                 FileDirectory fileDirectory) {
        this(applicationRepository, server, versionState, stateMonitor, vipStatus, EXIT_JVM,
             vipStatusMode(applicationRepository), fileDirectory);
    }

    protected ConfigServerBootstrap(ApplicationRepository applicationRepository, RpcServer server,
                                    VersionState versionState, StateMonitor stateMonitor, VipStatus vipStatus,
                                    RedeployingApplicationsFails exitIfRedeployingApplicationsFails,
                                    VipStatusMode vipStatusMode, FileDirectory fileDirectory) {
        this.applicationRepository = applicationRepository;
        this.server = server;
        this.versionState = versionState;
        this.stateMonitor = stateMonitor;
        this.vipStatus = vipStatus;
        this.configserverConfig = applicationRepository.configserverConfig();
        this.maxDurationOfRedeployment = Duration.ofSeconds(configserverConfig.maxDurationOfBootstrap());
        this.sleepTimeWhenRedeployingFails = Duration.ofSeconds(configserverConfig.sleepTimeWhenRedeployingFails());
        this.exitIfRedeployingApplicationsFails = exitIfRedeployingApplicationsFails;
        this.clock = applicationRepository.clock();
        rpcServerExecutor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("config server RPC server"));
        configServerMaintenance = new ConfigServerMaintenance(applicationRepository, fileDirectory);
        configServerMaintenance.startBeforeBootstrap();
        log.log(Level.FINE, () -> "VIP status mode: " + vipStatusMode);
        initializing(vipStatusMode);
        start();
    }

    @Override
    public void deconstruct() {
        log.log(Level.INFO, "Stopping config server");
        down();
        server.stop();
        log.log(Level.FINE, "RPC server stopped");
        rpcServerExecutor.shutdown();
        configServerMaintenance.shutdown();
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
        startRpcServerWithFileDistribution(); // No config requests allowed yet, will be allowed after bootstrapping done
        if (versionState.isUpgraded()) {
            if ( ! versionState.storedVersion().equals(Version.emptyVersion))
                log.log(Level.INFO, "Config server upgrading from " + versionState.storedVersion() + " to "
                        + versionState.currentVersion() + ". Redeploying all applications");
            try {
                redeployAllApplications();
                versionState.storeCurrentVersion();
                log.log(Level.FINE, "All applications redeployed successfully");
            } catch (Exception e) {
                log.log(Level.SEVERE, "Redeployment of applications failed", e);
                redeployingApplicationsFailed();
                return; // Status will not be set to 'up' since we return here
            }
        }
        applicationRepository.bootstrappingDone();
        allowConfigRpcRequests(server);
        up();
        configServerMaintenance.startAfterBootstrap();
    }

    StateMonitor.Status status() { return stateMonitor.status(); }

    VipStatus vipStatus() { return vipStatus; }

    public ConfigServerMaintenance configServerMaintenance() { return configServerMaintenance; }

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

    private void startRpcServerWithFileDistribution() {
        rpcServerExecutor.execute(server);

        Instant end = clock.instant().plus(Duration.ofSeconds(10));
        while (!server.isRunning() && clock.instant().isBefore(end)) {
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

    private void allowConfigRpcRequests(RpcServer rpcServer) {
        log.log(Level.FINE, "Allowing RPC config requests");
        rpcServer.setUpGetConfigHandlers();
    }

    private void redeployingApplicationsFailed() {
        if (exitIfRedeployingApplicationsFails == EXIT_JVM) System.exit(1);
    }

    private void redeployAllApplications() throws InterruptedException {
        Instant end = clock.instant().plus(maxDurationOfRedeployment);
        List<ApplicationId> applicationsToRedeploy = new ArrayList<>(applicationRepository.listApplications());
        Collections.shuffle(applicationsToRedeploy);
        long failCount = 0;
        do {
            applicationsToRedeploy = redeployApplications(applicationsToRedeploy);
            if ( ! applicationsToRedeploy.isEmpty() && ! sleepTimeWhenRedeployingFails.isZero()) {
                Duration sleepTime = sleepTimeWhenRedeployingFails.multipliedBy(++failCount);
                if (sleepTime.compareTo(Duration.ofMinutes(10)) > 0)
                    sleepTime = Duration.ofMinutes(10);
                log.log(Level.INFO, "Redeployment of " + applicationsToRedeploy + " not finished, will retry in " + sleepTime);
                Thread.sleep(sleepTime.toMillis());
            }
        } while ( ! applicationsToRedeploy.isEmpty() && clock.instant().isBefore(end));

        if ( ! applicationsToRedeploy.isEmpty())
            throw new RuntimeException("Redeploying applications not finished after " + maxDurationOfRedeployment +
                                       ", exiting, applications that failed redeployment: " + applicationsToRedeploy);
    }

    // Returns the set of applications that failed to redeploy
    private List<ApplicationId> redeployApplications(List<ApplicationId> applicationIds) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(configserverConfig.numRedeploymentThreads(),
                                                                new DaemonThreadFactory("redeploy-apps-"));
        // Keep track of deployment status per application
        Map<ApplicationId, Future<?>> deployments = new HashMap<>();
        if (applicationIds.size() > 0) {
            log.log(Level.INFO, () -> "Redeploying " + applicationIds.size() + " apps " + applicationIds + " with " +
                    configserverConfig.numRedeploymentThreads() + " threads");
            applicationIds.forEach(appId -> deployments.put(appId, executor.submit(() -> {
                log.log(Level.INFO, () -> "Starting redeployment of " + appId);
                applicationRepository.deployFromLocalActive(appId, true /* bootstrap */)
                                     .ifPresent(Deployment::activate);
                log.log(Level.INFO, () -> appId + " redeployed");
            })));
        }

        List<ApplicationId> failedDeployments = checkDeployments(deployments);

        executor.shutdown();
        if (! executor.awaitTermination(5, TimeUnit.HOURS)) {
            log.log(Level.SEVERE, () -> "Unable to shutdown " + executor + ", waited 5 hours. Exiting");
            System.exit(1);
        }

        return failedDeployments;
    }

    private enum DeploymentStatus { inProgress, done, failed }

    private List<ApplicationId> checkDeployments(Map<ApplicationId, Future<?>> deployments) {
        int applicationCount = deployments.size();
        Set<ApplicationId> failedDeployments = new LinkedHashSet<>();
        Set<ApplicationId> finishedDeployments = new LinkedHashSet<>();
        LogState logState = new LogState(applicationCount);

        do {
            deployments.forEach((applicationId, future) -> {
                if (finishedDeployments.contains(applicationId) || failedDeployments.contains(applicationId)) return;

                DeploymentStatus status = getDeploymentStatus(applicationId, future);
                switch (status) {
                    case done:
                        finishedDeployments.add(applicationId);
                        break;
                    case inProgress:
                        break;
                    case failed:
                        failedDeployments.add(applicationId);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown deployment status " + status);
                }
            });
            logProgress(logState, failedDeployments.size(), finishedDeployments.size());
        } while (failedDeployments.size() + finishedDeployments.size() < applicationCount);

        return new ArrayList<>(failedDeployments);
    }

    private void logProgress(LogState logState, int failedDeployments, int finishedDeployments) {
        if ( ! Duration.between(logState.lastLogged, clock.instant()).minus(Duration.ofSeconds(10)).isNegative()
                && (logState.failedDeployments != failedDeployments || logState.finishedDeployments != finishedDeployments)) {
            log.log(Level.INFO, () -> finishedDeployments + " of " + logState.applicationCount + " apps redeployed " +
                    "(" + failedDeployments + " failed)");
            logState.update(clock.instant(), failedDeployments, finishedDeployments);
        }
    }

    private DeploymentStatus getDeploymentStatus(ApplicationId applicationId, Future<?> future) {
        try {
            future.get(1, TimeUnit.MILLISECONDS);
            return DeploymentStatus.done;
        } catch (ExecutionException | InterruptedException e) {
            if (e.getCause() instanceof TransientException) {
                log.log(Level.INFO, "Redeploying " + applicationId +
                                    " failed with transient error, will retry after bootstrap: " + Exceptions.toMessageString(e));
            } else {
                log.log(Level.WARNING, "Redeploying " + applicationId + " failed, will retry", e);
            }
            return DeploymentStatus.failed;
        } catch (TimeoutException e) {
            return DeploymentStatus.inProgress;
        }
    }

    private static VipStatusMode vipStatusMode(ApplicationRepository applicationRepository) {
        return applicationRepository.configserverConfig().hostedVespa()
                ? VipStatusMode.VIP_STATUS_FILE
                : VipStatusMode.VIP_STATUS_PROGRAMMATICALLY;
    }

    private static class LogState {

        private final int applicationCount;

        private Instant lastLogged = Instant.EPOCH;
        private int failedDeployments = 0;
        private int finishedDeployments = 0;

        public LogState(int applicationCount) {
            this.applicationCount = applicationCount;
        }

        public void update(Instant lastLogged, int failedDeployments, int finishedDeployments) {
            this.lastLogged = lastLogged;
            this.failedDeployments = failedDeployments;
            this.finishedDeployments = finishedDeployments;
        }

    }

}

