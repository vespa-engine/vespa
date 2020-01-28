// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.Notifications;
import com.yahoo.config.application.api.Notifications.When;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.log.LogLevel;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.ActivateResult;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Hostname;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ServiceConvergence;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.DeploymentFailureMails;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.maintenance.JobRunner;
import com.yahoo.yolean.Exceptions;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyPair;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.config.application.api.Notifications.Role.author;
import static com.yahoo.config.application.api.Notifications.When.failing;
import static com.yahoo.config.application.api.Notifications.When.failingCommit;
import static com.yahoo.vespa.hosted.controller.api.integration.configserver.Node.State.active;
import static com.yahoo.vespa.hosted.controller.api.integration.configserver.Node.State.reserved;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.aborted;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.deploymentFailed;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.error;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.installationFailed;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.outOfCapacity;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.running;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.testFailure;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployInitialReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installInitialReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installTester;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toList;

/**
 * Runs steps of a deployment job against its provided controller.
 *
 * A dual-purpose logger is set up for each step run here:
 *   1. all messages are logged to a buffer which is stored in an external log storage at the end of execution, and
 *   2. all messages are also logged through the usual logging framework; by default, any messages of level
 *      {@code Level.INFO} or higher end up in the Vespa log, and all messages may be sent there by means of log-control.
 *
 * @author jonmv
 */
public class InternalStepRunner implements StepRunner {

    private static final Logger logger = Logger.getLogger(InternalStepRunner.class.getName());
    private static final NodeResources DEFAULT_TESTER_RESOURCES =
            new NodeResources(1, 4, 50, 0.3, NodeResources.DiskSpeed.any);
    // Must match exactly the advertised resources of an AWS instance type. Also consider that the container
    // will have ~1.8 GB less memory than equivalent resources in AWS (VESPA-16259).
    private static final NodeResources DEFAULT_TESTER_RESOURCES_AWS =
            new NodeResources(2, 8, 50, 0.3, NodeResources.DiskSpeed.any);

    static final Duration endpointTimeout = Duration.ofMinutes(15);
    static final Duration testerTimeout = Duration.ofMinutes(30);
    static final Duration installationTimeout = Duration.ofMinutes(60);
    static final Duration certificateTimeout = Duration.ofMinutes(300);

    private final Controller controller;
    private final TestConfigSerializer testConfigSerializer;
    private final DeploymentFailureMails mails;

    public InternalStepRunner(Controller controller) {
        this.controller = controller;
        this.testConfigSerializer = new TestConfigSerializer(controller.system());
        this.mails = new DeploymentFailureMails(controller.zoneRegistry());
    }

    @Override
    public Optional<RunStatus> run(LockedStep step, RunId id) {
        DualLogger logger = new DualLogger(id, step.get());
        try {
            switch (step.get()) {
                case deployTester: return deployTester(id, logger);
                case deployInitialReal: return deployInitialReal(id, logger);
                case installInitialReal: return installInitialReal(id, logger);
                case deployReal: return deployReal(id, logger);
                case installTester: return installTester(id, logger);
                case installReal: return installReal(id, logger);
                case startStagingSetup: return startTests(id, true, logger);
                case endStagingSetup: return endTests(id, logger);
                case startTests: return startTests(id, false, logger);
                case endTests: return endTests(id, logger);
                case copyVespaLogs: return copyVespaLogs(id, logger);
                case deactivateReal: return deactivateReal(id, logger);
                case deactivateTester: return deactivateTester(id, logger);
                case report: return report(id, logger);
                default: throw new AssertionError("Unknown step '" + step + "'!");
            }
        }
        catch (UncheckedIOException e) {
            logger.logWithInternalException(INFO, "IO exception running " + id + ": " + Exceptions.toMessageString(e), e);
            return Optional.empty();
        }
        catch (RuntimeException e) {
            logger.log(WARNING, "Unexpected exception running " + id, e);
            if (JobProfile.of(id.type()).alwaysRun().contains(step.get())) {
                logger.log("Will keep trying, as this is a cleanup step.");
                return Optional.empty();
            }
            return Optional.of(error);
        }
    }

    private Optional<RunStatus> deployInitialReal(RunId id, DualLogger logger) {
        Versions versions = controller.jobController().run(id).get().versions();
        logger.log("Deploying platform version " +
                   versions.sourcePlatform().orElse(versions.targetPlatform()) +
                   " and application version " +
                   versions.sourceApplication().orElse(versions.targetApplication()).id() + " ...");
        return deployReal(id, true, versions, logger);
    }

    private Optional<RunStatus> deployReal(RunId id, DualLogger logger) {
        Versions versions = controller.jobController().run(id).get().versions();
        logger.log("Deploying platform version " + versions.targetPlatform() +
                         " and application version " + versions.targetApplication().id() + " ...");
        return deployReal(id, false, versions, logger);
    }

    private Optional<RunStatus> deployReal(RunId id, boolean setTheStage, Versions versions, DualLogger logger) {
        Optional<ApplicationPackage> applicationPackage = id.type().environment().isManuallyDeployed()
                ? Optional.of(new ApplicationPackage(controller.applications().applicationStore()
                                                               .getDev(id.application(), id.type().zone(controller.system()))))
                : Optional.empty();

        Optional<Version> vespaVersion = id.type().environment().isManuallyDeployed()
                ? Optional.of(versions.targetPlatform())
                : Optional.empty();
        return deploy(id.application(),
                      id.type(),
                      () -> controller.applications().deploy(id.application(),
                                                             id.type().zone(controller.system()),
                                                             applicationPackage,
                                                             new DeployOptions(false,
                                                                               vespaVersion,
                                                                               false,
                                                                               setTheStage)),
                      controller.jobController().run(id).get()
                                .stepInfo(setTheStage ? deployInitialReal : deployReal).get()
                                .startTime().get(),
                      logger);
    }

    private Optional<RunStatus> deployTester(RunId id, DualLogger logger) {
        Version platform = controller.systemVersion();
        logger.log("Deploying the tester container on platform " + platform + " ...");
        return deploy(id.tester().id(),
                      id.type(),
                      () -> controller.applications().deployTester(id.tester(),
                                                                   testerPackage(id),
                                                                   id.type().zone(controller.system()),
                                                                   new DeployOptions(true,
                                                                                     Optional.of(platform),
                                                                                     false,
                                                                                     false)),
                      controller.jobController().run(id).get()
                                .stepInfo(deployTester).get()
                                .startTime().get(),
                      logger);
    }

    private Optional<RunStatus> deploy(ApplicationId id, JobType type, Supplier<ActivateResult> deployment,
                                       Instant startTime, DualLogger logger) {
        try {
            PrepareResponse prepareResponse = deployment.get().prepareResponse();
            if ( ! prepareResponse.configChangeActions.refeedActions.stream().allMatch(action -> action.allowed)) {
                List<String> messages = new ArrayList<>();
                messages.add("Deploy failed due to non-compatible changes that require re-feed.");
                messages.add("Your options are:");
                messages.add("1. Revert the incompatible changes.");
                messages.add("2. If you think it is safe in your case, you can override this validation, see");
                messages.add("   http://docs.vespa.ai/documentation/reference/validation-overrides.html");
                messages.add("3. Deploy as a new application under a different name.");
                messages.add("Illegal actions:");
                prepareResponse.configChangeActions.refeedActions.stream()
                                                                 .filter(action -> ! action.allowed)
                                                                 .flatMap(action -> action.messages.stream())
                                                                 .forEach(messages::add);
                messages.add("Details:");
                prepareResponse.log.stream()
                                   .map(entry -> entry.message)
                                   .forEach(messages::add);
                logger.log(messages);
                return Optional.of(deploymentFailed);
            }

            if (prepareResponse.configChangeActions.restartActions.isEmpty())
                logger.log("No services requiring restart.");
            else
                prepareResponse.configChangeActions.restartActions.stream()
                                                                  .flatMap(action -> action.services.stream())
                                                                  .map(service -> service.hostName)
                                                                  .sorted().distinct()
                                                                  .map(Hostname::new)
                                                                  .forEach(hostname -> {
                                                                      controller.applications().restart(new DeploymentId(id, type.zone(controller.system())), Optional.of(hostname));
                                                                      logger.log("Restarting services on host " + hostname.id() + ".");
                                                                  });
            logger.log("Deployment successful.");
            if (prepareResponse.message != null)
                logger.log(prepareResponse.message);
            return Optional.of(running);
        }
        catch (ConfigServerException e) {
            // Retry certain failures for up to one hour.
            Optional<RunStatus> result = startTime.isBefore(controller.clock().instant().minus(Duration.ofHours(1)))
                                         ? Optional.of(deploymentFailed) : Optional.empty();
            switch (e.getErrorCode()) {
                case ACTIVATION_CONFLICT:
                case APPLICATION_LOCK_FAILURE:
                case CERTIFICATE_NOT_READY:
                    logger.log("Deployment failed with possibly transient error " + e.getErrorCode() +
                            ", will retry: " + e.getMessage());
                    return result;
                case LOAD_BALANCER_NOT_READY:
                case PARENT_HOST_NOT_READY:
                    logger.log(e.getServerMessage());
                    return result;
                case OUT_OF_CAPACITY:
                    logger.log(e.getServerMessage());
                    return Optional.of(outOfCapacity);
                case INVALID_APPLICATION_PACKAGE:
                case BAD_REQUEST:
                    logger.log(e.getMessage());
                    return Optional.of(deploymentFailed);
            }

            throw e;
        }
    }

    private Optional<RunStatus> installInitialReal(RunId id, DualLogger logger) {
        return installReal(id, true, logger);
    }

    private Optional<RunStatus> installReal(RunId id, DualLogger logger) {
        return installReal(id, false, logger);
    }

    private Optional<RunStatus> installReal(RunId id, boolean setTheStage, DualLogger logger) {
        Optional<Deployment> deployment = deployment(id.application(), id.type());
        if (deployment.isEmpty()) {
            logger.log(INFO, "Deployment expired before installation was successful.");
            return Optional.of(installationFailed);
        }

        Versions versions = controller.jobController().run(id).get().versions();
        Version platform = setTheStage ? versions.sourcePlatform().orElse(versions.targetPlatform()) : versions.targetPlatform();

        Run run = controller.jobController().run(id).get();
        Optional<ServiceConvergence> services = controller.serviceRegistry().configServer().serviceConvergence(new DeploymentId(id.application(), id.type().zone(controller.system())),
                                                                                                               Optional.of(platform));
        if (services.isEmpty()) {
            logger.log("Config status not currently available -- will retry.");
            return Optional.empty();
        }
        List<Node> nodes = controller.serviceRegistry().configServer().nodeRepository().list(id.type().zone(controller.system()),
                                                                                             id.application(),
                                                                                             ImmutableSet.of(active, reserved));
        List<Node> parents = controller.serviceRegistry().configServer().nodeRepository().list(id.type().zone(controller.system()),
                                                                                               nodes.stream().map(node -> node.parentHostname().get()).collect(toList()));
        NodeList nodeList = NodeList.of(nodes, parents, services.get());
        boolean firstTick = run.convergenceSummary().isEmpty();
        if (firstTick) { // Run the first time (for each convergence step).
            logger.log(nodeList.asList().stream()
                               .flatMap(node -> nodeDetails(node, true))
                               .collect(toList()));
        }
        ConvergenceSummary summary = nodeList.summary();
        if (summary.converged()) {
            controller.jobController().locked(id, lockedRun -> lockedRun.withSummary(null));
            if (endpointsAvailable(id.application(), id.type().zone(controller.system()), logger)) {
                if (containersAreUp(id.application(), id.type().zone(controller.system()), logger)) {
                    logger.log("Installation succeeded!");
                    return Optional.of(running);
                }
            }
            else if (timedOut(id, deployment.get(), endpointTimeout)) {
                logger.log(WARNING, "Endpoints failed to show up within " + endpointTimeout.toMinutes() + " minutes!");
                return Optional.of(error);
            }
        }

        boolean failed = false;

        NodeList suspendedTooLong = nodeList.suspendedSince(controller.clock().instant().minus(installationTimeout));
        if ( ! suspendedTooLong.isEmpty()) {
            logger.log(INFO, "Some nodes have been suspended for more than " + installationTimeout.toMinutes() + " minutes.");
            failed = true;
        }

        if (run.noNodesDownSince()
               .map(since -> since.isBefore(controller.clock().instant().minus(installationTimeout)))
               .orElse(false)) {
            if (summary.needPlatformUpgrade() > 0 || summary.needReboot() > 0 || summary.needRestart() > 0)
                logger.log(INFO, "No nodes allowed to suspend to progress installation for " + installationTimeout.toMinutes() + " minutes.");
            else
                logger.log(INFO, "Nodes not able to start with new application package.");
            failed = true;
        }

        Duration timeout = JobRunner.jobTimeout.minusHours(1); // Time out before job dies.
        if (timedOut(id, deployment.get(), timeout)) {
            logger.log(INFO, "Installation failed to complete within " + timeout.toHours() + "hours!");
            failed = true;
        }

        if (failed) {
            logger.log(nodeList.asList().stream()
                               .flatMap(node -> nodeDetails(node, true))
                               .collect(toList()));
            return Optional.of(installationFailed);
        }

        if ( ! firstTick)
            logger.log(nodeList.expectedDown().asList().stream()
                               .flatMap(node -> nodeDetails(node, false))
                               .collect(toList()));

        controller.jobController().locked(id, lockedRun -> {
            Instant noNodesDownSince = nodeList.allowedDown().size() == 0 ? lockedRun.noNodesDownSince().orElse(controller.clock().instant()) : null;
            return lockedRun.noNodesDownSince(noNodesDownSince).withSummary(summary);
        });

        return Optional.empty();
    }

    private Optional<RunStatus> installTester(RunId id, DualLogger logger) {
        Run run = controller.jobController().run(id).get();
        Version platform = controller.systemVersion();
        ZoneId zone = id.type().zone(controller.system());
        ApplicationId testerId = id.tester().id();

        Optional<ServiceConvergence> services = controller.serviceRegistry().configServer().serviceConvergence(new DeploymentId(testerId, zone),
                                                                                                               Optional.of(platform));
        if (services.isEmpty()) {
            logger.log("Config status not currently available -- will retry.");
            return run.stepInfo(installTester).get().startTime().get().isBefore(controller.clock().instant().minus(Duration.ofMinutes(5)))
                   ? Optional.of(error)
                   : Optional.empty();
        }
        List<Node> nodes = controller.serviceRegistry().configServer().nodeRepository().list(zone,
                                                                                             testerId,
                                                                                             ImmutableSet.of(active, reserved));
        List<Node> parents = controller.serviceRegistry().configServer().nodeRepository().list(zone,
                                                                                               nodes.stream().map(node -> node.parentHostname().get()).collect(toList()));
        NodeList nodeList = NodeList.of(nodes, parents, services.get());
        logger.log(nodeList.asList().stream()
                           .flatMap(node -> nodeDetails(node, false))
                           .collect(toList()));
        if (nodeList.summary().converged()) {
            if (endpointsAvailable(testerId, zone, logger)) {
                if (testerContainersAreUp(testerId, zone, logger)) {
                    logger.log("Tester container successfully installed!");
                    return Optional.of(running);
                }
            }
            else if (run.stepInfo(installTester).get().startTime().get().plus(endpointTimeout).isBefore(controller.clock().instant())) {
                logger.log(WARNING, "Tester failed to show up within " + endpointTimeout.toMinutes() + " minutes!");
                return Optional.of(error);
            }
        }

        if (run.stepInfo(installTester).get().startTime().get().plus(testerTimeout).isBefore(controller.clock().instant())) {
            logger.log(WARNING, "Installation of tester failed to complete within " + testerTimeout.toMinutes() + " minutes!");
            return Optional.of(error);
        }

        return Optional.empty();
    }

    /** Returns true iff all containers in the deployment give 100 consecutive 200 OK responses on /status.html. */
    // TODO: Change implementation to only be used for real deployments when useConfigServerForTesterAPI() always returns true
    private boolean containersAreUp(ApplicationId id, ZoneId zoneId, DualLogger logger) {
        var endpoints = controller.applications().clusterEndpoints(Set.of(new DeploymentId(id, zoneId)));
        if ( ! endpoints.containsKey(zoneId))
            return false;

        for (URI endpoint : endpoints.get(zoneId).values()) {
            boolean ready = id.instance().isTester() ? controller.jobController().cloud().testerReady(endpoint)
                                                     : controller.jobController().cloud().ready(endpoint);

            if (!ready) {
                logger.log("Failed to get 100 consecutive OKs from " + endpoint);
                return false;
            }
        }

        return true;
    }

    /** Returns true iff all containers in the tester deployment give 100 consecutive 200 OK responses on /status.html. */
    private boolean testerContainersAreUp(ApplicationId id, ZoneId zoneId, DualLogger logger) {
        if (useConfigServerForTesterAPI(zoneId)) {
            DeploymentId deploymentId = new DeploymentId(id, zoneId);
            if (controller.jobController().cloud().testerReady(deploymentId)) {
                return true;
            } else {
                logger.log("Failed to get 100 consecutive OKs from tester container for " + deploymentId);
                return false;
            }
        } else {
            return containersAreUp(id, zoneId, logger);
        }
    }


    private boolean endpointsAvailable(ApplicationId id, ZoneId zone, DualLogger logger) {
        if (useConfigServerForTesterAPI(zone) && id.instance().isTester()) return true; // Endpoints not used in this case, always return true

        var endpoints = controller.applications().clusterEndpoints(Set.of(new DeploymentId(id, zone)));
        if ( ! endpoints.containsKey(zone)) {
            logger.log("Endpoints not yet ready.");
            return false;
        }
        for (var endpoint : endpoints.get(zone).values())
            if ( ! controller.jobController().cloud().exists(endpoint)) {
                logger.log(INFO, "DNS lookup yielded no IP address for '" + endpoint + "'.");
                return false;
            }

        logEndpoints(endpoints, logger);
        return true;
    }

    private void logEndpoints(Map<ZoneId, Map<ClusterSpec.Id, URI>> endpoints, DualLogger logger) {
        List<String> messages = new ArrayList<>();
        messages.add("Found endpoints:");
        endpoints.forEach((zone, uris) -> {
            messages.add("- " + zone);
            uris.forEach((cluster, uri) -> messages.add(" |-- " + uri + " (" + cluster + ")"));
        });
        logger.log(messages);
    }

    private Stream<String> nodeDetails(NodeWithServices node, boolean printAllServices) {
        return Stream.concat(Stream.of(node.node().hostname() + ": " + humanize(node.node().serviceState()),
                                       "--- platform " + node.node().wantedVersion() + (node.needsPlatformUpgrade()
                                                                                        ? " <-- " + (node.node().currentVersion().isEmpty() ? "not booted" : node.node().currentVersion())
                                                                                        : "") +
                                       (node.needsOsUpgrade() && node.isAllowedDown()
                                        ? ", upgrading OS (" + node.node().wantedOsVersion() + " <-- " + node.node().currentOsVersion() + ")"
                                        : "") +
                                       (node.needsFirmwareUpgrade() && node.isAllowedDown()
                                        ? ", upgrading firmware"
                                        : "") +
                                       (node.needsRestart()
                                        ? ", restart pending (" + node.node().wantedRestartGeneration() + " <-- " + node.node().restartGeneration() + ")"
                                        : "") +
                                       (node.needsReboot()
                                        ? ", reboot pending (" + node.node().wantedRebootGeneration() + " <-- " + node.node().rebootGeneration() + ")"
                                        : "")),
                             node.services().stream()
                                 .filter(service -> printAllServices || node.needsNewConfig())
                                 .map(service -> "--- " + service.type() + " on port " + service.port() + (service.currentGeneration() == -1
                                                                                                           ? " has not started "
                                                                                                           : " has config generation " + service.currentGeneration() + ", wanted is " + node.wantedConfigGeneration())));
    }

    private String humanize(Node.ServiceState state) {
        switch (state) {
            case allowedDown: return "allowed to be DOWN";
            case expectedUp: return "expected to be UP";
            case unorchestrated: return "unorchestrated";
            default: return state.name();
        }
    }

    private Optional<RunStatus> startTests(RunId id, boolean isSetup, DualLogger logger) {
        Optional<Deployment> deployment = deployment(id.application(), id.type());
        if (deployment.isEmpty()) {
            logger.log(INFO, "Deployment expired before tests could start.");
            return Optional.of(error);
        }

        var deployments = controller.applications().requireInstance(id.application())
                                    .productionDeployments().keySet().stream()
                                    .map(zone -> new DeploymentId(id.application(), zone))
                                    .collect(Collectors.toSet());
        ZoneId zoneId = id.type().zone(controller.system());
        deployments.add(new DeploymentId(id.application(), zoneId));

        logger.log("Attempting to find endpoints ...");
        var endpoints = controller.applications().clusterEndpoints(deployments);
        if ( ! endpoints.containsKey(zoneId)) {
            logger.log(WARNING, "Endpoints for the deployment to test vanished again, while it was still active!");
            return Optional.of(error);
        }
        logEndpoints(endpoints, logger);

        Optional<URI> testerEndpoint = controller.jobController().testerEndpoint(id);
        if (useConfigServerForTesterAPI(zoneId)) {
            if ( ! controller.jobController().cloud().testerReady(getTesterDeploymentId(id))) {
                logger.log(WARNING, "Tester container went bad!");
                return Optional.of(error);
            }
        } else {
            if (testerEndpoint.isEmpty()) {
                logger.log(WARNING, "Endpoints for the tester container vanished again, while it was still active!");
                return Optional.of(error);
            }

            if ( ! controller.jobController().cloud().testerReady(testerEndpoint.get())) {
                logger.log(WARNING, "Tester container went bad!");
                return Optional.of(error);
            }
        }

        logger.log("Starting tests ...");
        TesterCloud.Suite suite = TesterCloud.Suite.of(id.type(), isSetup);
        byte[] config = testConfigSerializer.configJson(id.application(),
                                                        id.type(),
                                                        true,
                                                        endpoints,
                                                        controller.applications().contentClustersByZone(deployments));
        if (useConfigServerForTesterAPI(zoneId)) {
            controller.jobController().cloud().startTests(getTesterDeploymentId(id), suite, config);
        } else {
            controller.jobController().cloud().startTests(testerEndpoint.get(), suite, config);
        }
        return Optional.of(running);
    }

    private Optional<RunStatus> endTests(RunId id, DualLogger logger) {
        if (deployment(id.application(), id.type()).isEmpty()) {
            logger.log(INFO, "Deployment expired before tests could complete.");
            return Optional.of(aborted);
        }

        Optional<X509Certificate> testerCertificate = controller.jobController().run(id).get().testerCertificate();
        if (testerCertificate.isPresent()) {
            try {
                testerCertificate.get().checkValidity(Date.from(controller.clock().instant()));
            }
            catch (CertificateExpiredException | CertificateNotYetValidException e) {
                logger.log(INFO, "Tester certificate expired before tests could complete.");
                return Optional.of(aborted);
            }
        }

        controller.jobController().updateTestLog(id);

        TesterCloud.Status testStatus;
        if (useConfigServerForTesterAPI(id.type().zone(controller.system()))) {
            testStatus = controller.jobController().cloud().getStatus(getTesterDeploymentId(id));
        } else {
            Optional<URI> testerEndpoint = controller.jobController().testerEndpoint(id);
            if (testerEndpoint.isEmpty()) {
                logger.log("Endpoints for tester not found -- trying again later.");
                return Optional.empty();
            }
            testStatus = controller.jobController().cloud().getStatus(testerEndpoint.get());
        }

        switch (testStatus) {
            case NOT_STARTED:
                throw new IllegalStateException("Tester reports tests not started, even though they should have!");
            case RUNNING:
                return Optional.empty();
            case FAILURE:
                logger.log("Tests failed.");
                return Optional.of(testFailure);
            case ERROR:
                logger.log(INFO, "Tester failed running its tests!");
                return Optional.of(error);
            case SUCCESS:
                logger.log("Tests completed successfully.");
                return Optional.of(running);
            default:
                throw new IllegalStateException("Unknown status '" + testStatus + "'!");
        }
    }

    private Optional<RunStatus> copyVespaLogs(RunId id, DualLogger logger) {
        if (deployment(id.application(), id.type()).isPresent())
            try {
                controller.jobController().updateVespaLog(id);
            }
            catch (Exception e) {
                logger.log(INFO, "Failure getting vespa logs for " + id, e);
                return Optional.of(error);
            }
        return Optional.of(running);
    }

    private Optional<RunStatus> deactivateReal(RunId id, DualLogger logger) {
        try {
            logger.log("Deactivating deployment of " + id.application() + " in " + id.type().zone(controller.system()) + " ...");
            controller.applications().deactivate(id.application(), id.type().zone(controller.system()));
            return Optional.of(running);
        }
        catch (RuntimeException e) {
            logger.log(WARNING, "Failed deleting application " + id.application(), e);
            Instant startTime = controller.jobController().run(id).get().stepInfo(deactivateReal).get().startTime().get();
            return startTime.isBefore(controller.clock().instant().minus(Duration.ofHours(1)))
                   ? Optional.of(error)
                   : Optional.empty();
        }
    }

    private Optional<RunStatus> deactivateTester(RunId id, DualLogger logger) {
        try {
            logger.log("Deactivating tester of " + id.application() + " in " + id.type().zone(controller.system()) + " ...");
            controller.jobController().deactivateTester(id.tester(), id.type());
            return Optional.of(running);
        }
        catch (RuntimeException e) {
            logger.log(WARNING, "Failed deleting tester of " + id.application(), e);
            Instant startTime = controller.jobController().run(id).get().stepInfo(deactivateTester).get().startTime().get();
            return startTime.isBefore(controller.clock().instant().minus(Duration.ofHours(1)))
                   ? Optional.of(error)
                   : Optional.empty();
        }
    }

    private Optional<RunStatus> report(RunId id, DualLogger logger) {
        try {
            controller.jobController().active(id).ifPresent(run -> {
                if (run.hasFailed())
                    sendNotification(run, logger);
            });
        }
        catch (IllegalStateException e) {
            logger.log(INFO, "Job '" + id.type() + "' no longer supposed to run?", e);
            return Optional.of(error);
        }
        return Optional.of(running);
    }

    /** Sends a mail with a notification of a failed run, if one should be sent. */
    private void sendNotification(Run run, DualLogger logger) {
        Application application = controller.applications().requireApplication(TenantAndApplicationId.from(run.id().application()));
        Notifications notifications = application.deploymentSpec().requireInstance(run.id().application().instance()).notifications();
        boolean newCommit = application.require(run.id().application().instance()).change().application()
                                    .map(run.versions().targetApplication()::equals)
                                    .orElse(false);
        When when = newCommit ? failingCommit : failing;

        List<String> recipients = new ArrayList<>(notifications.emailAddressesFor(when));
        if (notifications.emailRolesFor(when).contains(author))
            run.versions().targetApplication().authorEmail().ifPresent(recipients::add);

        if (recipients.isEmpty())
            return;

        try {
            if (run.status() == outOfCapacity && run.id().type().isProduction())
                controller.serviceRegistry().mailer().send(mails.outOfCapacity(run.id(), recipients));
            if (run.status() == deploymentFailed)
                controller.serviceRegistry().mailer().send(mails.deploymentFailure(run.id(), recipients));
            if (run.status() == installationFailed)
                controller.serviceRegistry().mailer().send(mails.installationFailure(run.id(), recipients));
            if (run.status() == testFailure)
                controller.serviceRegistry().mailer().send(mails.testFailure(run.id(), recipients));
            if (run.status() == error)
                controller.serviceRegistry().mailer().send(mails.systemError(run.id(), recipients));
        }
        catch (RuntimeException e) {
            logger.log(INFO, "Exception trying to send mail for " + run.id(), e);
        }
    }

    /** Returns the deployment of the real application in the zone of the given job, if it exists. */
    private Optional<Deployment> deployment(ApplicationId id, JobType type) {
        return Optional.ofNullable(application(id).deployments().get(type.zone(controller.system())));
    }

    /** Returns the real application with the given id. */
    private Instance application(ApplicationId id) {
        controller.applications().lockApplicationOrThrow(TenantAndApplicationId.from(id), __ -> { }); // Memory fence.
        return controller.applications().requireInstance(id);
    }

    /**
     * Returns whether the time since deployment is more than the zone deployment expiry, or the given timeout.
     *
     * We time out the job before the deployment expires, for zones where deployments are not persistent,
     * to be able to collect the Vespa log from the deployment. Thus, the lower of the zone's deployment expiry,
     * and the given default installation timeout, minus one minute, is used as a timeout threshold.
     */
    private boolean timedOut(RunId id, Deployment deployment, Duration defaultTimeout) {
        // TODO jonmv: This is a workaround for new deployment writes not yet being visible in spite of Curator locking.
        // TODO Investigate what's going on here, and remove this workaround.
        Run run = controller.jobController().run(id).get();
        if ( ! controller.system().isCd() && run.start().isAfter(deployment.at()))
            return false;

        Duration timeout = controller.zoneRegistry().getDeploymentTimeToLive(deployment.zone())
                                     .filter(zoneTimeout -> zoneTimeout.compareTo(defaultTimeout) < 0)
                                     .orElse(defaultTimeout);
        return deployment.at().isBefore(controller.clock().instant().minus(timeout.minus(Duration.ofMinutes(1))));
    }

    /** Returns the application package for the tester application, assembled from a generated config, fat-jar and services.xml. */
    private ApplicationPackage testerPackage(RunId id) {
        ApplicationVersion version = controller.jobController().run(id).get().versions().targetApplication();
        DeploymentSpec spec = controller.applications().requireApplication(TenantAndApplicationId.from(id.application())).deploymentSpec();

        ZoneId zone = id.type().zone(controller.system());
        boolean useTesterCertificate = controller.system().isPublic() && id.type().environment().isTest();

        byte[] servicesXml = servicesXml(controller.zoneRegistry().accessControlDomain(),
                                         ! controller.system().isPublic(),
                                         useTesterCertificate,
                                         testerFlavorFor(id, spec)
                                                 .map(NodeResources::fromLegacyName)
                                                 .orElse(zone.region().value().contains("aws-") ?
                                                         DEFAULT_TESTER_RESOURCES_AWS : DEFAULT_TESTER_RESOURCES));
        byte[] testPackage = controller.applications().applicationStore().getTester(id.application().tenant(), id.application().application(), version);
        byte[] deploymentXml = deploymentXml(id.tester(),
                                             spec.athenzDomain(),
                                             spec.requireInstance(id.application().instance()).athenzService(zone.environment(), zone.region()));

        try (ZipBuilder zipBuilder = new ZipBuilder(testPackage.length + servicesXml.length + 1000)) {
            zipBuilder.add(testPackage);
            zipBuilder.add("services.xml", servicesXml);
            zipBuilder.add("deployment.xml", deploymentXml);
            if (useTesterCertificate)
                appendAndStoreCertificate(zipBuilder, id);

            zipBuilder.close();
            return new ApplicationPackage(zipBuilder.toByteArray());
        }
    }

    private void appendAndStoreCertificate(ZipBuilder zipBuilder, RunId id) {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 2048);
        X500Principal subject = new X500Principal("CN=" + id.tester().id().toFullString() + "." + id.type() + "." + id.number());
        X509Certificate certificate = X509CertificateBuilder.fromKeypair(keyPair,
                                                                         subject,
                                                                         controller.clock().instant(),
                                                                         controller.clock().instant().plus(certificateTimeout),
                                                                         SignatureAlgorithm.SHA512_WITH_RSA,
                                                                         BigInteger.valueOf(1))
                                                            .build();
        controller.jobController().storeTesterCertificate(id, certificate);
        zipBuilder.add("artifacts/key", KeyUtils.toPem(keyPair.getPrivate()).getBytes(UTF_8));
        zipBuilder.add("artifacts/cert", X509CertificateUtils.toPem(certificate).getBytes(UTF_8));
    }

    private DeploymentId getTesterDeploymentId(RunId runId) {
        ZoneId zoneId = runId.type().zone(controller.system());
        return new DeploymentId(runId.tester().id(), zoneId);
    }

    private boolean useConfigServerForTesterAPI(ZoneId zoneId) {
        BooleanFlag useConfigServerForTesterAPI = Flags.USE_CONFIG_SERVER_FOR_TESTER_API_CALLS.bindTo(controller.flagSource());
        boolean useConfigServer = useConfigServerForTesterAPI.with(FetchVector.Dimension.ZONE_ID, zoneId.value()).value();
        InternalStepRunner.logger.log(LogLevel.INFO, Flags.USE_CONFIG_SERVER_FOR_TESTER_API_CALLS.id().toString() +
                                                     " has value " + useConfigServer + " in zone " + zoneId.value());
        return useConfigServer;
    }

    private static Optional<String> testerFlavorFor(RunId id, DeploymentSpec spec) {
        for (DeploymentSpec.Step step : spec.steps())
            if (step.concerns(id.type().environment()))
                return step.zones().get(0).testerFlavor();

        return Optional.empty();
    }

    /** Returns the generated services.xml content for the tester application. */
    static byte[] servicesXml(AthenzDomain domain, boolean systemUsesAthenz, boolean useTesterCertificate,
                              NodeResources resources) {
        int jdiscMemoryGb = 2; // 2Gb memory for tester application (excessive?).
        int jdiscMemoryPct = (int) Math.ceil(100 * jdiscMemoryGb / resources.memoryGb());

        // Of the remaining memory, split 50/50 between Surefire running the tests and the rest
        int testMemoryMb = (int) (1024 * (resources.memoryGb() - jdiscMemoryGb) / 2);

        String resourceString = String.format(Locale.ENGLISH,
                                              "<resources vcpu=\"%.2f\" memory=\"%.2fGb\" disk=\"%.2fGb\" disk-speed=\"%s\" storage-type=\"%s\"/>",
                                              resources.vcpu(), resources.memoryGb(), resources.diskGb(), resources.diskSpeed().name(), resources.storageType().name());

        AthenzDomain idDomain = ("vespa.vespa.cd".equals(domain.value()) ? AthenzDomain.from("vespa.vespa") : domain);
        String servicesXml =
                "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<services xmlns:deploy='vespa' version='1.0'>\n" +
                "    <container version='1.0' id='tester'>\n" +
                "\n" +
                "        <component id=\"com.yahoo.vespa.hosted.testrunner.TestRunner\" bundle=\"vespa-testrunner-components\">\n" +
                "            <config name=\"com.yahoo.vespa.hosted.testrunner.test-runner\">\n" +
                "                <artifactsPath>artifacts</artifactsPath>\n" +
                "                <surefireMemoryMb>" + testMemoryMb + "</surefireMemoryMb>\n" +
                "                <useAthenzCredentials>" + systemUsesAthenz + "</useAthenzCredentials>\n" +
                "                <useTesterCertificate>" + useTesterCertificate + "</useTesterCertificate>\n" +
                "            </config>\n" +
                "        </component>\n" +
                "\n" +
                "        <handler id=\"com.yahoo.vespa.hosted.testrunner.TestRunnerHandler\" bundle=\"vespa-testrunner-components\">\n" +
                "            <binding>http://*/tester/v1/*</binding>\n" +
                "        </handler>\n" +
                "\n" +
                "        <http>\n" +
                "            <!-- Make sure 4080 is the first port. This will be used by the config server. -->\n" +
                "            <server id='default' port='4080'/>\n" +
                "            <server id='testertls4443' port='4443'>\n" +
                "                <config name=\"jdisc.http.connector\">\n" +
                "                    <tlsClientAuthEnforcer>\n" +
                "                        <enable>true</enable>\n" +
                "                        <pathWhitelist>\n" +
                "                            <item>/status.html</item>\n" +
                "                            <item>/state/v1/config</item>\n" +
                "                        </pathWhitelist>\n" +
                "                    </tlsClientAuthEnforcer>\n" +
                "                </config>\n" +
                "                <ssl>\n" +
                "                    <private-key-file>/var/lib/sia/keys/" + idDomain.value() + ".tenant.key.pem</private-key-file>\n" +
                "                    <certificate-file>/var/lib/sia/certs/" + idDomain.value() + ".tenant.cert.pem</certificate-file>\n" +
                "                    <ca-certificates-file>/opt/yahoo/share/ssl/certs/athenz_certificate_bundle.pem</ca-certificates-file>\n" +
                "                    <client-authentication>want</client-authentication>\n" +
                "                </ssl>\n" +
                "            </server>\n" +
                "            <filtering>\n" +
                (systemUsesAthenz ?
                "                <access-control domain='" + domain.value() + "'>\n" + // Set up dummy access control to pass validation :/
                "                    <exclude>\n" +
                "                        <binding>http://*/tester/v1/*</binding>\n" +
                "                    </exclude>\n" +
                "                </access-control>\n"
                : "") +
                "                <request-chain id=\"testrunner-api\">\n" +
                "                    <filter id='authz-filter' class='com.yahoo.jdisc.http.filter.security.athenz.AthenzAuthorizationFilter' bundle=\"jdisc-security-filters\">\n" +
                "                        <config name=\"jdisc.http.filter.security.athenz.athenz-authorization-filter\">\n" +
                "                            <credentialsToVerify>TOKEN_ONLY</credentialsToVerify>\n" +
                "                            <roleTokenHeaderName>Yahoo-Role-Auth</roleTokenHeaderName>\n" +
                "                        </config>\n" +
                "                        <component id=\"com.yahoo.jdisc.http.filter.security.athenz.StaticRequestResourceMapper\" bundle=\"jdisc-security-filters\">\n" +
                "                            <config name=\"jdisc.http.filter.security.athenz.static-request-resource-mapper\">\n" +
                "                                <resourceName>" + domain.value() + ":tester-application</resourceName>\n" +
                "                                <action>deploy</action>\n" +
                "                            </config>\n" +
                "                        </component>\n" +
                "                    </filter>\n" +
                "                </request-chain>\n" +
                "            </filtering>\n" +
                "        </http>\n" +
                "\n" +
                "        <accesslog type='json' fileNamePattern='logs/vespa/qrs/access-json.%Y%m%d%H%M%S'/>\n" +
                "\n" +
                "        <nodes count=\"1\" allocated-memory=\"" + jdiscMemoryPct + "%\">\n" +
                "            " + resourceString + "\n" +
                "        </nodes>\n" +
                "    </container>\n" +
                "</services>\n";

        return servicesXml.getBytes(UTF_8);
    }

    /** Returns a dummy deployment xml which sets up the service identity for the tester, if present. */
    private static byte[] deploymentXml(TesterId id, Optional<AthenzDomain> athenzDomain, Optional<AthenzService> athenzService) {
        String deploymentSpec =
                "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<deployment version=\"1.0\" " +
                athenzDomain.map(domain -> "athenz-domain=\"" + domain.value() + "\" ").orElse("") +
                athenzService.map(service -> "athenz-service=\"" + service.value() + "\" ").orElse("") + ">" +
                "  <instance id=\"" + id.id().instance().value() + "\" />" +
                "</deployment>";
        return deploymentSpec.getBytes(UTF_8);
    }

    /** Logger which logs to a {@link JobController}, as well as to the parent class' {@link Logger}. */
    private class DualLogger {

        private final RunId id;
        private final Step step;

        private DualLogger(RunId id, Step step) {
            this.id = id;
            this.step = step;
        }

        private void log(String... messages) {
            log(List.of(messages));
        }

        private void log(List<String> messages) {
            controller.jobController().log(id, step, INFO, messages);
        }

        private void log(Level level, String message) {
            log(level, message, null);
        }

        // Print stack trace in our logs, but don't expose it to end users
        private void logWithInternalException(Level level, String message, Throwable thrown) {
            logger.log(level, id + " at " + step + ": " + message, thrown);
            controller.jobController().log(id, step, level, message);
        }

        private void log(Level level, String message, Throwable thrown) {
            logger.log(level, id + " at " + step + ": " + message, thrown);

            if (thrown != null) {
                ByteArrayOutputStream traceBuffer = new ByteArrayOutputStream();
                thrown.printStackTrace(new PrintStream(traceBuffer));
                message += "\n" + traceBuffer;
            }
            controller.jobController().log(id, step, level, message);
        }

    }

}
