// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import java.util.ArrayList;
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
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.config.server.ConfigServerBootstrap.Mode.BOOTSTRAP_IN_CONSTRUCTOR;
import static com.yahoo.vespa.config.server.ConfigServerBootstrap.Mode.FOR_TESTING_NO_BOOTSTRAP_OF_APPS;
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

    enum Mode { BOOTSTRAP_IN_CONSTRUCTOR, FOR_TESTING_NO_BOOTSTRAP_OF_APPS}
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
                          StateMonitor stateMonitor, VipStatus vipStatus, VipStatusMode vipStatusMode) {
        this(applicationRepository, server, versionState, stateMonitor, vipStatus,
             FOR_TESTING_NO_BOOTSTRAP_OF_APPS, CONTINUE, vipStatusMode);
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

        log.log(Level.FINE, () -> "Bootstrap mode: " + mode + ", VIP status mode: " + vipStatusMode);
        initializing(vipStatusMode);

        switch (mode) {
            case BOOTSTRAP_IN_CONSTRUCTOR:
                start();
                break;
            case FOR_TESTING_NO_BOOTSTRAP_OF_APPS:
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
        List<ApplicationId> applicationsToRedeploy = applicationRepository.listApplications();
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
        } while ( ! applicationsToRedeploy.isEmpty() && Instant.now().isBefore(end));

        if ( ! applicationsToRedeploy.isEmpty()) {
            log.log(Level.SEVERE, "Redeploying applications not finished after " + maxDurationOfRedeployment +
                    ", exiting, applications that failed redeployment: " + applicationsToRedeploy);
            return false;
        }
        return true;
    }

    // Returns applications that failed to redeploy
    private List<ApplicationId> redeployApplications(List<ApplicationId> applicationIds) throws InterruptedException {
        ExecutorService executor = getExecutor();
        log.log(Level.INFO, () -> "Redeploying " + applicationIds.size() + " apps: " + applicationIds);

        PreparedApplications preparedApplications = prepare(applicationIds, executor);
        if (preparedApplications.hasPrepareFailures()) {
            log.log(Level.INFO, "Failed preparing applications: " + preparedApplications.failed());
            return preparedApplications.failed();
        }

        List<ApplicationId> failed = activate(preparedApplications.deploymentInfos());

        shutdownExecutor(executor);
        return failed;
    }

    private void shutdownExecutor(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        if ( ! executor.awaitTermination(1, TimeUnit.HOURS)) {
            log.log(Level.WARNING, "Awaiting termination of executor failed");
            executor.shutdownNow();
        }
    }

    private ExecutorService getExecutor() {
        return Executors.newFixedThreadPool(configserverConfig.numRedeploymentThreads(),
                                            new DaemonThreadFactory("redeploy-apps-"));
    }

    private PreparedApplications prepare(List<ApplicationId> applicationIds, ExecutorService executor) {
        Map<ApplicationId, Future<Optional<Deployment>>> prepared = new HashMap<>();
        applicationIds.forEach(appId -> prepared.put(appId, executor.submit(() -> {
            log.log(Level.INFO, () -> "Preparing " + appId);
            Optional<Deployment> deployment = applicationRepository.deployFromLocalActive(appId, true /* bootstrap */);
            if (deployment.isPresent()) {
                deployment.get().prepare();
                log.log(Level.INFO, () -> appId + " prepared");
            } else {
                log.log(Level.INFO, () -> "No deployment present for appId, not prepared");
            }
            return deployment;
        })));

        return waitForPrepare(prepared);
    }

    private List<ApplicationId> activate(List<DeploymentInfo> deployments) {
        List<ApplicationId> failedActivations = new ArrayList<>();
        deployments.forEach(d -> {
            ApplicationId applicationId = d.applicationId();
            log.log(Level.INFO, () -> "Activating " + applicationId);
            try {
                d.deployment().ifPresent(Deployment::activate);
            } catch (Exception e) {
                log.log(Level.INFO, () -> "Failed activating " + applicationId + ":" + e.getMessage());
                failedActivations.add(applicationId);
            }
            log.log(Level.INFO, () -> applicationId + " activated");
        });
        return failedActivations;
    }

    private PreparedApplications waitForPrepare(Map<ApplicationId, Future<Optional<Deployment>>> deployments) {
        int applicationCount = deployments.size();
        PreparedApplications applications = new PreparedApplications();
        Instant lastLogged = Instant.EPOCH;

        do {
            deployments.forEach((applicationId, future) -> {
                if (applications.prepareFinished(applicationId)) return;

                DeploymentInfo status = getDeploymentStatus(applicationId, future);
                switch (status.status()) {
                    case success:
                    case failed:
                        applications.add(status);
                        break;
                    case inProgress:
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown deployment status " + status);
                }
            });
            if ( ! Duration.between(lastLogged, Instant.now()).minus(Duration.ofSeconds(10)).isNegative()) {
                logProgress(applicationCount, applications);
                lastLogged = Instant.now();
            }
        } while (applications.failCount() + applications.successCount() < applicationCount);

        logProgress(applicationCount, applications);
        return applications;
    }

    private void logProgress(int applicationCount, PreparedApplications preparedApplications) {
        log.log(Level.INFO, () -> preparedApplications.successCount() + " of " + applicationCount + " apps prepared " +
                                  "(" + preparedApplications.failCount() + " failed)");
    }

    private DeploymentInfo getDeploymentStatus(ApplicationId applicationId, Future<Optional<Deployment>> future) {
        try {
            Optional<Deployment> deployment = future.get(1, TimeUnit.MILLISECONDS);
            return new DeploymentInfo(applicationId, DeploymentInfo.Status.success, deployment);
        } catch (ExecutionException | InterruptedException e) {
            if (e.getCause() instanceof TransientException) {
                log.log(Level.INFO, "Preparing" + " " + applicationId +
                                    " failed with transient error, will retry after bootstrap: " + Exceptions.toMessageString(e));
            } else {
                log.log(Level.WARNING, "Preparing" + " " + applicationId + " failed, will retry", e);
            }
            return new DeploymentInfo(applicationId, DeploymentInfo.Status.failed);
        } catch (TimeoutException e) {
            return new DeploymentInfo(applicationId, DeploymentInfo.Status.inProgress);
        }
    }

    private static class DeploymentInfo {

        public enum Status { inProgress, success, failed }

        private final ApplicationId applicationId;
        private final Status status;
        private final Optional<Deployment> deployment;

        public DeploymentInfo(ApplicationId applicationId, Status status) {
            this(applicationId, status, Optional.empty());
        }

        public DeploymentInfo(ApplicationId applicationId, Status status, Optional<Deployment> deployment) {
            this.applicationId = applicationId;
            this.status = status;
            this.deployment = deployment;
        }

        public ApplicationId applicationId() { return applicationId; }

        public Status status() { return status; }

        public Optional<Deployment> deployment() { return deployment; }

    }

    private static class PreparedApplications {
        private final List<DeploymentInfo> deploymentInfos = new ArrayList<>();

        public void add(DeploymentInfo deploymentInfo) {
            this.deploymentInfos.add(deploymentInfo);
        }

        List<ApplicationId> success() { return withStatus(DeploymentInfo.Status.success); }

        List<ApplicationId> failed() { return withStatus(DeploymentInfo.Status.failed); }

        List<ApplicationId> withStatus(DeploymentInfo.Status status) {
            return deploymentInfos.stream()
                                  .filter(deploymentInfo -> deploymentInfo.status() == status)
                                  .map(DeploymentInfo::applicationId)
                                  .collect(Collectors.toList());
        }

        int successCount() { return success().size(); }

        int failCount() { return failed().size(); }

        boolean hasPrepareFailures() { return failCount() > 0; }

        List<DeploymentInfo> deploymentInfos() { return deploymentInfos; }

        boolean prepareFinished(ApplicationId applicationId) {
            return failed().contains(applicationId) || success().contains(applicationId);
        }

    }

}

