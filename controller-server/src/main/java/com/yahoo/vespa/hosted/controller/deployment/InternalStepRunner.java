package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.ActivateResult;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Hostname;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ServiceConvergence;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.log.LogLevel.DEBUG;
import static com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException.ErrorCode.ACTIVATION_CONFLICT;
import static com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException.ErrorCode.APPLICATION_LOCK_FAILURE;
import static com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException.ErrorCode.OUT_OF_CAPACITY;
import static com.yahoo.vespa.hosted.controller.api.integration.configserver.Node.State.active;
import static com.yahoo.vespa.hosted.controller.api.integration.configserver.Node.State.reserved;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.deploymentFailed;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.error;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.installationFailed;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.running;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.testFailure;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

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

    static final Duration endpointTimeout = Duration.ofMinutes(15);
    static final Duration installationTimeout = Duration.ofMinutes(150);

    // TODO jvenstad: Move this tester logic to the application controller, perhaps?
    public static ApplicationId testerOf(ApplicationId id) {
        return ApplicationId.from(id.tenant().value(),
                                  id.application().value(),
                                  id.instance().value() + "-t");
    }

    private final Controller controller;
    private final TesterCloud testerCloud;

    public InternalStepRunner(Controller controller, TesterCloud testerCloud) {
        this.controller = controller;
        this.testerCloud = testerCloud;
    }

    @Override
    public Optional<RunStatus> run(LockedStep step, RunId id) {
        ByteArrayLogger logger = ByteArrayLogger.of(id.application(), id.type(), step.get());
        try {
            switch (step.get()) {
                case deployInitialReal: return deployInitialReal(id, logger);
                case installInitialReal: return installInitialReal(id, logger);
                case deployReal: return deployReal(id, logger);
                case deployTester: return deployTester(id, logger);
                case installReal: return installReal(id, logger);
                case installTester: return installTester(id, logger);
                case startTests: return startTests(id, logger);
                case endTests: return endTests(id, logger);
                case deactivateReal: return deactivateReal(id, logger);
                case deactivateTester: return deactivateTester(id, logger);
                case report: return report(id);
                default: throw new AssertionError("Unknown step '" + step + "'!");
            }
        }
        catch (RuntimeException e) {
            logger.log(INFO, "Unexpected exception running " + id, e);
            if (JobProfile.of(id.type()).alwaysRun().contains(step.get())) {
                logger.log("Will keep trying, as this is a cleanup step.");
                return Optional.empty();
            }
            return Optional.of(error);
        }
        finally {
            controller.jobController().log(id, step.get(), logger.getLog());
        }
    }

    private Optional<RunStatus> deployInitialReal(RunId id, ByteArrayLogger logger) {
        Versions versions = controller.jobController().run(id).get().versions();
        logger.log("Deploying platform version " +
                   versions.sourcePlatform().orElse(versions.targetPlatform()) +
                   " and application version " +
                   versions.sourceApplication().orElse(versions.targetApplication()).id() + " ...");
        return deployReal(id, true, logger);
    }

    private Optional<RunStatus> deployReal(RunId id, ByteArrayLogger logger) {
        Versions versions = controller.jobController().run(id).get().versions();
        logger.log("Deploying platform version " + versions.targetPlatform() +
                         " and application version " + versions.targetApplication().id() + " ...");
        return deployReal(id, false, logger);
    }

    private Optional<RunStatus> deployReal(RunId id, boolean setTheStage, ByteArrayLogger logger) {
        return deploy(id.application(),
                      id.type(),
                      () -> controller.applications().deploy(id.application(),
                                                             id.type().zone(controller.system()),
                                                             Optional.empty(),
                                                             new DeployOptions(false,
                                                                               Optional.empty(),
                                                                               false,
                                                                               setTheStage)),
                      logger);
    }

    private Optional<RunStatus> deployTester(RunId id, ByteArrayLogger logger) {
        // TODO jvenstad: Consider deploying old version of tester for initial staging feeding?
        logger.log("Deploying the tester container ...");
        return deploy(testerOf(id.application()),
                      id.type(),
                      () -> controller.applications().deployTester(testerOf(id.application()),
                                                                   testerPackage(id),
                                                                   id.type().zone(controller.system()),
                                                                   new DeployOptions(true,
                                                                                     Optional.of(controller.systemVersion()),
                                                                                     false,
                                                                                     false)),
                      logger);
    }

    private Optional<RunStatus> deploy(ApplicationId id, JobType type, Supplier<ActivateResult> deployment, ByteArrayLogger logger) {
        try {
            PrepareResponse prepareResponse = deployment.get().prepareResponse();
            if ( ! prepareResponse.configChangeActions.refeedActions.stream().allMatch(action -> action.allowed)) {
                logger.log("Deploy failed due to non-compatible changes that require re-feed. " +
                                        "Your options are: \n" +
                                        "1. Revert the incompatible changes.\n" +
                                        "2. If you think it is safe in your case, you can override this validation, see\n" +
                                        "   http://docs.vespa.ai/documentation/reference/validation-overrides.html\n" +
                                        "3. Deploy as a new application under a different name.\n" +
                                        "Illegal actions:\n" +
                                        prepareResponse.configChangeActions.refeedActions.stream()
                                                                                         .filter(action -> ! action.allowed)
                                                                                         .flatMap(action -> action.messages.stream())
                                                                                         .collect(Collectors.joining("\n")) + "\n" +
                                        "Details:\n" +
                                        prepareResponse.log.stream()
                                                           .map(entry -> entry.message)
                                                           .collect(Collectors.joining("\n")));
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
            return Optional.of(running);
        }
        catch (ConfigServerException e) {
            if (   e.getErrorCode() == OUT_OF_CAPACITY && type.isTest()
                || e.getErrorCode() == ACTIVATION_CONFLICT
                || e.getErrorCode() == APPLICATION_LOCK_FAILURE) {
                logger.log("Will retry, because of '" + e.getErrorCode() + "' deploying:\n" + e.getMessage());
                return Optional.empty();
            }
            throw e;
        }
    }

    private Optional<RunStatus> installInitialReal(RunId id, ByteArrayLogger logger) {
        return installReal(id, true, logger);
    }

    private Optional<RunStatus> installReal(RunId id, ByteArrayLogger logger) {
        return installReal(id, false, logger);
    }

    private Optional<RunStatus> installReal(RunId id, boolean setTheStage, ByteArrayLogger logger) {
        Versions versions = controller.jobController().run(id).get().versions();
        Version platform = setTheStage ? versions.sourcePlatform().orElse(versions.targetPlatform()) : versions.targetPlatform();
        ApplicationVersion application = setTheStage ? versions.sourceApplication().orElse(versions.targetApplication()) : versions.targetApplication();
        logger.log("Checking installation of " + platform + " and " + application + " ...");

        if (nodesConverged(id.application(), id.type(), platform, logger) && servicesConverged(id.application(), id.type())) {
            logger.log("Installation succeeded!");
            return Optional.of(running);
        }

        if (timedOut(id.application(), id.type(), installationTimeout)) {
            logger.log(INFO, "Installation failed to complete within " + installationTimeout.toMinutes() + " minutes!");
            return Optional.of(installationFailed);
        }

        logger.log("Installation not yet complete.");
        return Optional.empty();
    }

    private Optional<RunStatus> installTester(RunId id, ByteArrayLogger logger) {
        logger.log("Checking installation of tester container ...");

        if (servicesConverged(testerOf(id.application()), id.type())) {
            logger.log("Tester container successfully installed!");
            return Optional.of(running);
        }

        if (timedOut(id.application(), id.type(), installationTimeout)) {
            logger.log(WARNING, "Installation of tester failed to complete within " + installationTimeout.toMinutes() + " minutes of real deployment!");
            return Optional.of(error);
        }

        logger.log("Installation of tester not yet complete.");
        return Optional.empty();
    }

    private boolean nodesConverged(ApplicationId id, JobType type, Version target, ByteArrayLogger logger) {
        List<Node> nodes = controller.configServer().nodeRepository().list(type.zone(controller.system()), id, ImmutableSet.of(active, reserved));
        for (Node node : nodes)
            logger.log(String.format("%70s: %-16s%-25s%-32s%s",
                                           node.hostname(),
                                           node.serviceState(),
                                           node.wantedVersion() + (node.currentVersion().equals(node.wantedVersion()) ? "" : " <-- " + node.currentVersion()),
                                           node.restartGeneration() == node.wantedRestartGeneration() ? ""
                                                   : "restart pending (" + node.wantedRestartGeneration() + " <-- " + node.restartGeneration() + ")",
                                           node.rebootGeneration() == node.wantedRebootGeneration() ? ""
                                                   : "reboot pending (" + node.wantedRebootGeneration() + " <-- " + node.rebootGeneration() + ")"));

        return nodes.stream().allMatch(node ->    node.currentVersion().equals(target)
                                               && node.restartGeneration() == node.wantedRestartGeneration()
                                               && node.rebootGeneration() == node.wantedRebootGeneration());
    }

    private boolean servicesConverged(ApplicationId id, JobType type) {
        // TODO jvenstad: Print information for each host.
        return controller.configServer().serviceConvergence(new DeploymentId(id, type.zone(controller.system())))
                         .map(ServiceConvergence::converged)
                         .orElse(false);
    }

    private Optional<RunStatus> startTests(RunId id, ByteArrayLogger logger) {
        logger.log("Attempting to find endpoints ...");
        Map<ZoneId, List<URI>> endpoints = deploymentEndpoints(id.application());
        logger.log("Found endpoints:\n" +
                         endpoints.entrySet().stream()
                                  .map(zoneEndpoints -> "- " + zoneEndpoints.getKey() + ":\n" +
                                                        zoneEndpoints.getValue().stream()
                                                                     .map(uri -> " |-- " + uri)
                                                                     .collect(Collectors.joining("\n")))
                                  .collect(Collectors.joining("\n")));
        if ( ! endpoints.containsKey(id.type().zone(controller.system()))) {
            if (timedOut(id.application(), id.type(), endpointTimeout)) {
                logger.log(WARNING, "Endpoints failed to show up within " + endpointTimeout.toMinutes() + " minutes!");
                return Optional.of(error);
            }

            logger.log("Endpoints for the deployment to test are not yet ready.");
            return Optional.empty();
        }

        Optional<URI> testerEndpoint = testerEndpoint(id);
        if (testerEndpoint.isPresent()) {
            logger.log("Starting tests ...");
            testerCloud.startTests(testerEndpoint.get(),
                                   TesterCloud.Suite.of(id.type()),
                                   testConfig(id.application(), id.type().zone(controller.system()), controller.system(), endpoints));
            return Optional.of(running);
        }

        if (timedOut(id.application(), id.type(), endpointTimeout)) {
            logger.log(WARNING, "Endpoint for tester failed to show up within " + endpointTimeout.toMinutes() + " minutes of real deployment!");
            return Optional.of(error);
        }

        logger.log("Endpoints of tester container not yet available.");
        return Optional.empty();
    }

    private Optional<RunStatus> endTests(RunId id, ByteArrayLogger logger) {
        URI testerEndpoint = testerEndpoint(id)
                .orElseThrow(() -> new NoSuchElementException("Endpoint for tester vanished again before tests were complete!"));

        RunStatus status;
        switch (testerCloud.getStatus(testerEndpoint)) {
            case NOT_STARTED:
                throw new IllegalStateException("Tester reports tests not started, even though they should have!");
            case RUNNING:
                return Optional.empty();
            case FAILURE:
                logger.log("Tests failed.");
                status = testFailure; break;
            case ERROR:
                logger.log(INFO, "Tester failed running its tests!");
                status = error; break;
            case SUCCESS:
                logger.log("Tests completed successfully.");
                status = running; break;
            default:
                throw new AssertionError("Unknown status!");
        }
        logger.log(new String(testerCloud.getLogs(testerEndpoint))); // TODO jvenstad: Replace with something less hopeless!
        return Optional.of(status);
    }

    private Optional<RunStatus> deactivateReal(RunId id, ByteArrayLogger logger) {
        logger.log("Deactivating deployment of " + id.application() + " in " + id.type().zone(controller.system()) + " ...");
        controller.applications().deactivate(id.application(), id.type().zone(controller.system()));
        return Optional.of(running);
    }

    private Optional<RunStatus> deactivateTester(RunId id, ByteArrayLogger logger) {
        logger.log("Deactivating tester of " + id.application() + " in " + id.type().zone(controller.system()) + " ...");
        controller.jobController().deactivateTester(id.application(), id.type());
        return Optional.of(running);
    }

    private Optional<RunStatus> report(RunId id) {
        controller.jobController().active(id).ifPresent(run -> controller.applications().deploymentTrigger().notifyOfCompletion(report(run)));
        return Optional.of(running);
    }

    /** Returns the real application with the given id. */
    private Application application(ApplicationId id) {
        return controller.applications().require(id);
    }

    /** Returns whether the time elapsed since the last real deployment in the given zone is more than the given timeout. */
    private boolean timedOut(ApplicationId id, JobType type, Duration timeout) {
        return application(id).deployments().get(type.zone(controller.system())).at().isBefore(controller.clock().instant().minus(timeout));
    }

    /** Returns a generated job report for the given run. */
    private DeploymentJobs.JobReport report(Run run) {
        return new DeploymentJobs.JobReport(run.id().application(),
                                            run.id().type(),
                                            1,
                                            run.id().number(),
                                            Optional.empty(),
                                            run.hasFailed() ? Optional.of(DeploymentJobs.JobError.unknown) : Optional.empty());
    }

    /** Returns the application package for the tester application, assembled from a generated config, fat-jar and services.xml. */
    private ApplicationPackage testerPackage(RunId id) {
        ApplicationVersion version = controller.jobController().run(id).get().versions().targetApplication();

        byte[] testPackage = controller.applications().applicationStore().getTesterPackage(testerOf(id.application()), version.id());
        byte[] servicesXml = servicesXml(controller.system());

        try (ZipBuilder zipBuilder = new ZipBuilder(testPackage.length + servicesXml.length + 1000)) {
            zipBuilder.add(testPackage);
            zipBuilder.add("services.xml", servicesXml);
            return new ApplicationPackage(zipBuilder.toByteArray());
        }
    }

    /** Returns all endpoints for all current deployments of the given real application. */
    private Map<ZoneId, List<URI>> deploymentEndpoints(ApplicationId id) {
        ImmutableMap.Builder<ZoneId, List<URI>> deployments = ImmutableMap.builder();
        application(id).deployments().keySet()
                  .forEach(zone -> controller.applications().getDeploymentEndpoints(new DeploymentId(id, zone))
                                             .filter(endpoints -> ! endpoints.isEmpty())
                                             .ifPresent(endpoints -> deployments.put(zone, endpoints)));
        return deployments.build();
    }

    /** Returns a URI of the tester endpoint retrieved from the routing generator, provided it matches an expected form. */
    private Optional<URI> testerEndpoint(RunId id) {
        ApplicationId tester = testerOf(id.application());
        return controller.applications().getDeploymentEndpoints(new DeploymentId(tester, id.type().zone(controller.system())))
                         .flatMap(uris -> uris.stream()
                                              .filter(uri -> uri.getHost().contains(String.format("%s--%s--%s.",
                                                                                                  tester.instance().value(),
                                                                                                  tester.application().value(),
                                                                                                  tester.tenant().value())))
                                              .findAny());
    }

    /** Returns the generated services.xml content for the tester application. */
    static byte[] servicesXml(SystemName systemName) {
        String domain = systemName == SystemName.main ? "vespa.vespa" : "vespa.vespa.cd";

        String servicesXml = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<services xmlns:deploy='vespa' version='1.0'>\n" +
                "    <container version='1.0' id='default'>\n" +
                "\n" +
                "        <component id=\"com.yahoo.vespa.hosted.testrunner.TestRunner\" bundle=\"vepsa-testrunner-components\">\n" +
                "            <config name=\"com.yahoo.vespa.hosted.testrunner.test-runner\">\n" +
                "                <artifactsPath>artifacts</artifactsPath>\n" +
                "            </config>\n" +
                "        </component>\n" +
                "\n" +
                "        <handler id=\"com.yahoo.vespa.hosted.testrunner.TestRunnerHandler\" bundle=\"vespa-testrunner-components\">\n" +
                "            <binding>http://*/tester/v1/*</binding>\n" +
                "        </handler>\n" +
                "\n" +
                "        <http>\n" +
                "            <filtering>\n" +
                "                <request-chain id=\"testrunner-api\">\n" +
                "                    <filter id='authz-filter' class='com.yahoo.jdisc.http.filter.security.athenz.AthenzAuthorizationFilter' bundle=\"jdisc-security-filters\">\n" +
                "                        <config name=\"jdisc.http.filter.security.athenz.athenz-authorization-filter\">\n" +
                "                            <credentialsToVerify>TOKEN_ONLY</credentialsToVerify>\n" +
                "                            <roleTokenHeaderName>Yahoo-Role-Auth</roleTokenHeaderName>\n" +
                "                        </config>\n" +
                "                        <component id=\"com.yahoo.jdisc.http.filter.security.athenz.StaticRequestResourceMapper\" bundle=\"jdisc-security-filters\">\n" +
                "                            <config name=\"jdisc.http.filter.security.athenz.static-request-resource-mapper\">\n" +
                "                                <resourceName>" + domain + ":tester-application</resourceName>\n" +
                "                                <action>deploy</action>\n" +
                "                            </config>\n" +
                "                        </component>\n" +
                "                    </filter>\n" +
                "                </request-chain>\n" +
                "            </filtering>\n" +
                "        </http>\n" +
                "\n" +
                "        <nodes count=\"1\" flavor=\"d-2-8-50\" />\n" +
                "    </container>\n" +
                "</services>\n";

        return servicesXml.getBytes();
    }

    /** Returns the config for the tests to run for the given job. */
    private static byte[] testConfig(ApplicationId id, ZoneId testerZone, SystemName system, Map<ZoneId, List<URI>> deployments) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("application", id.serializedForm());
        root.setString("zone", testerZone.value());
        root.setString("system", system.name());
        Cursor endpointsObject = root.setObject("endpoints");
        deployments.forEach((zone, endpoints) -> {
            Cursor endpointArray = endpointsObject.setArray(zone.value());
            for (URI endpoint : endpoints)
                endpointArray.addString(endpoint.toString());
        });
        try {
            return SlimeUtils.toJsonBytes(slime);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Logger which logs all records to a private byte array, as well as to its parent. */
    static class ByteArrayLogger extends Logger {

        private static final Logger parent = Logger.getLogger(InternalStepRunner.class.getName());
        private static final SimpleDateFormat timestampFormat = new SimpleDateFormat("[HH:mm:ss.SSS] ");
        static { timestampFormat.setTimeZone(TimeZone.getTimeZone("UTC")); }

        private final ByteArrayOutputStream bytes;
        private final PrintStream out;

        private ByteArrayLogger(Logger parent, String suffix) {
            super(parent.getName() + suffix, null);
            setParent(parent);

            bytes = new ByteArrayOutputStream();
            out = new PrintStream(bytes);
        }

        static ByteArrayLogger of(ApplicationId id, JobType type, Step step) {
            return new ByteArrayLogger(parent, String.format(".%s.%s.%s", id.toString(), type.jobName(), step));
        }

        @Override
        public void log(LogRecord record) {
            // TODO jvenstad: Store log records in a serialised format.
            String timestamp = timestampFormat.format(new Date(record.getMillis()));
            for (String line : record.getMessage().split("\n"))
                out.println(timestamp + ": " + line);

            record.setSourceClassName(null); // Makes the root logger's ConsoleHandler use the logger name instead, when printing.
            getParent().log(record);
        }

        public void log(String message) {
            log(DEBUG, message);
        }

        @Override
        public boolean isLoggable(Level __) {
            return true;
        }

        public byte[] getLog() {
            out.flush();
            return bytes.toByteArray();
        }

    }

}
