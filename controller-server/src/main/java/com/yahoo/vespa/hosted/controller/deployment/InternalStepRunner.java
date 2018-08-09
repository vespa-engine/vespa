package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableMap;
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
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NoInstanceException;
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
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step.Status;
import com.yahoo.yolean.Exceptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Arrays;
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
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.failed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Runs steps of a deployment job against its provided controller.
 *
 * A dual-purpose logger is set up for each thread that runs a step here:
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
    private final ThreadLocal<ByteArrayLogger> logger = new ThreadLocal<>();

    public InternalStepRunner(Controller controller, TesterCloud testerCloud) {
        this.controller = controller;
        this.testerCloud = testerCloud;
    }

    @Override
    public Status run(LockedStep step, RunId id) {
        try {
            logger.set(ByteArrayLogger.of(id.application(), id.type(), step.get()));
            switch (step.get()) {
                case deployInitialReal: return deployInitialReal(id);
                case installInitialReal: return installInitialReal(id);
                case deployReal: return deployReal(id);
                case deployTester: return deployTester(id);
                case installReal: return installReal(id);
                case installTester: return installTester(id);
                case startTests: return startTests(id);
                case endTests: return endTests(id);
                case deactivateReal: return deactivateReal(id);
                case deactivateTester: return deactivateTester(id);
                case report: return report(id);
                default: throw new AssertionError("Unknown step '" + step + "'!");
            }
        }
        catch (RuntimeException e) {
            logger.get().log(INFO, "Unexpected exception: " + Exceptions.toMessageString(e));
            return failed;
        }
        finally {
            controller.jobController().log(id, step.get(), logger.get().getLog());
            logger.remove();
        }
    }

    private Status deployInitialReal(RunId id) {
        JobStatus.JobRun triggering = triggering(id.application(), id.type());
        logger.get().log("Deploying platform version " +
                         triggering.sourcePlatform().orElse(triggering.platform()) +
                        " and application version " +
                         triggering.sourceApplication().orElse(triggering.application()) + " ...");
        return deployReal(id, true);
    }

    private Status deployReal(RunId id) {
        JobStatus.JobRun triggering = triggering(id.application(), id.type());
        logger.get().log("Deploying platform version " + triggering.platform() +
                         " and application version " + triggering.application() + " ...");
        return deployReal(id, false);
    }

    private Status deployReal(RunId id, boolean setTheStage) {
        return deploy(id.application(),
                      id.type(),
                      () -> controller.applications().deploy(id.application(),
                                                             zone(id.type()),
                                                             Optional.empty(),
                                                             new DeployOptions(false,
                                                                               Optional.empty(),
                                                                               false,
                                                                               setTheStage)));
    }

    private Status deployTester(RunId id) {
        // TODO jvenstad: Consider deploying old version of tester for initial staging feeding?
        logger.get().log("Deploying the tester container ...");
        return deploy(testerOf(id.application()),
                      id.type(),
                      () -> controller.applications().deployTester(testerOf(id.application()),
                                                                   testerPackage(id),
                                                                   zone(id.type()),
                                                                   new DeployOptions(true,
                                                                                     Optional.of(controller.systemVersion()),
                                                                                     false,
                                                                                     false)));
    }

    private Status deploy(ApplicationId id, JobType type, Supplier<ActivateResult> deployment) {
        try {
            PrepareResponse prepareResponse = deployment.get().prepareResponse();
            if ( ! prepareResponse.configChangeActions.refeedActions.stream().allMatch(action -> action.allowed)) {
                logger.get().log("Deploy failed due to non-compatible changes that require re-feed. " +
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
                return failed;
            }

            if (prepareResponse.configChangeActions.restartActions.isEmpty())
                logger.get().log("No services requiring restart.");
            else
                prepareResponse.configChangeActions.restartActions.stream()
                                                                  .flatMap(action -> action.services.stream())
                                                                  .map(service -> service.hostName)
                                                                  .sorted().distinct()
                                                                  .map(Hostname::new)
                                                                  .forEach(hostname -> {
                                                                      controller.applications().restart(new DeploymentId(id, zone(type)), Optional.of(hostname));
                                                                      logger.get().log("Restarting services on host " + hostname.id() + ".");
                                                                  });
            logger.get().log("Deployment successful.");
            return succeeded;
        }
        catch (ConfigServerException e) {
            if (   e.getErrorCode() == OUT_OF_CAPACITY && type.isTest()
                || e.getErrorCode() == ACTIVATION_CONFLICT
                || e.getErrorCode() == APPLICATION_LOCK_FAILURE) {
                logger.get().log("Will retry, because of '" + e.getErrorCode() + "' deploying:\n" + e.getMessage());
                return unfinished;
            }
            throw e;
        }
    }

    private Status installInitialReal(RunId id) {
        return installReal(id.application(), id.type(), true);
    }

    private Status installReal(RunId id) {
        return installReal(id.application(), id.type(), false);
    }

    private Status installReal(ApplicationId id, JobType type, boolean setTheStage) {
        JobStatus.JobRun triggering = triggering(id, type);
        Version platform = setTheStage ? triggering.sourcePlatform().orElse(triggering.platform()) : triggering.platform();
        ApplicationVersion application = setTheStage ? triggering.sourceApplication().orElse(triggering.application()) : triggering.application();
        logger.get().log("Checking installation of " + platform + " and " + application + " ...");

        if (nodesConverged(id, type, platform) && servicesConverged(id, type)) {
            logger.get().log("Installation succeeded!");
            return succeeded;
        }

        if (timedOut(id, type, installationTimeout)) {
            logger.get().log(INFO, "Installation failed to complete within " + installationTimeout.toMinutes() + " minutes!");
            return failed;
        }

        logger.get().log("Installation not yet complete.");
        return unfinished;
    }

    private Status installTester(RunId id) {
        logger.get().log("Checking installation of tester container ...");

        if (servicesConverged(testerOf(id.application()), id.type())) {
            logger.get().log("Tester container successfully installed!");
            return succeeded;
        }

        if (timedOut(id.application(), id.type(), installationTimeout)) {
            logger.get().log(WARNING, "Installation of tester failed to complete within " + installationTimeout.toMinutes() + " minutes of real deployment!");
            return failed;
        }

        logger.get().log("Installation of tester not yet complete.");
        return unfinished;
    }

    private boolean nodesConverged(ApplicationId id, JobType type, Version target) {
        List<Node> nodes = controller.configServer().nodeRepository().list(zone(type), id, Arrays.asList(active, reserved));
        for (Node node : nodes)
            logger.get().log(String.format("%70s: %-12s%-25s%-32s%s",
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
        return controller.configServer().serviceConvergence(new DeploymentId(id, zone(type)))
                         .map(ServiceConvergence::converged)
                         .orElse(false);
    }

    private Status startTests(RunId id) {
        logger.get().log("Attempting to find endpoints ...");
        Map<ZoneId, List<URI>> endpoints = deploymentEndpoints(id.application());
        logger.get().log("Found endpoints:\n" +
                         endpoints.entrySet().stream()
                                  .map(zoneEndpoints -> "- " + zoneEndpoints.getKey() + ":\n" +
                                                        zoneEndpoints.getValue().stream()
                                                                     .map(uri -> " |-- " + uri)
                                                                     .collect(Collectors.joining("\n"))));
        if ( ! endpoints.containsKey(zone(id.type()))) {
            if (timedOut(id.application(), id.type(), endpointTimeout)) {
                logger.get().log(WARNING, "Endpoints failed to show up within " + endpointTimeout.toMinutes() + " minutes!");
                return failed;
            }

            logger.get().log("Endpoints for the deployment to test are not yet ready.");
            return unfinished;
        }

        Optional<URI> testerEndpoint = testerEndpoint(id);
        if (testerEndpoint.isPresent()) {
            logger.get().log("Starting tests ...");
            testerCloud.startTests(testerEndpoint.get(),
                                   TesterCloud.Suite.of(id.type()),
                                   testConfig(id.application(), zone(id.type()), controller.system(), endpoints));
            return succeeded;
        }

        if (timedOut(id.application(), id.type(), installationTimeout)) {
            logger.get().log(WARNING, "Endpoint for tester failed to show up within " + installationTimeout.toMinutes() + " minutes of real deployment!");
            return failed;
        }

        logger.get().log("Endpoints of tester container not yet available.");
        return unfinished;
    }

    private Status endTests(RunId id) {
        URI testerEndpoint = testerEndpoint(id)
                .orElseThrow(() -> new NoSuchElementException("Endpoint for tester vanished again before tests were complete!"));

        Status status;
        switch (testerCloud.getStatus(testerEndpoint)) {
            case NOT_STARTED:
                throw new IllegalStateException("Tester reports tests not started, even though they should have!");
            case RUNNING:
                logger.get().log("Tests still running ...");
                return unfinished;
            case FAILURE:
                logger.get().log("Tests failed.");
                status = failed; break;
            case ERROR:
                logger.get().log(INFO, "Tester failed running its tests!");
                status = failed; break;
            case SUCCESS:
                logger.get().log("Tests completed successfully.");
                status = succeeded; break;
            default:
                throw new AssertionError("Unknown status!");
        }
        logger.get().log(new String(testerCloud.getLogs(testerEndpoint))); // TODO jvenstad: Replace with something less hopeless!
        return status;
    }

    private Status deactivateReal(RunId id) {
        logger.get().log("Deactivating deployment of " + id.application() + " in " + zone(id.type()) + " ...");
        return deactivate(id.application(), id.type());
    }

    private Status deactivateTester(RunId id) {
        logger.get().log("Deactivating tester of " + id.application() + " in " + zone(id.type()) + " ...");
        return deactivate(testerOf(id.application()), id.type());
    }

    private Status deactivate(ApplicationId id, JobType type) {
        try {
            controller.configServer().deactivate(new DeploymentId(id, zone(type)));
        }
        catch (NoInstanceException e) { }
        return succeeded;
    }

    private Status report(RunId id) {
        controller.jobController().active(id).ifPresent(run -> controller.applications().deploymentTrigger().notifyOfCompletion(report(run)));
        return succeeded;
    }

    /** Returns the real application with the given id. */
    private Application application(ApplicationId id) {
        return controller.applications().require(id);
    }

    /** Returns the zone of the given job type. */
    private ZoneId zone(JobType type) {
        return type.zone(controller.system()).get();
    }

    /** Returns the triggering of the currently running job, i.e., this job. */
    private JobStatus.JobRun triggering(ApplicationId id, JobType type) {
        return application(id).deploymentJobs().statusOf(type).get().lastTriggered().get();
    }

    /** Returns whether the time elapsed since the last real deployment in the given zone is more than the given timeout. */
    private boolean timedOut(ApplicationId id, JobType type, Duration timeout) {
        return application(id).deployments().get(zone(type)).at().isBefore(controller.clock().instant().minus(timeout));
    }

    /** Returns a generated job report for the given run. */
    private DeploymentJobs.JobReport report(RunStatus run) {
        return new DeploymentJobs.JobReport(run.id().application(),
                                            run.id().type(),
                                            Long.MAX_VALUE,
                                            run.id().number(),
                                            Optional.empty(),
                                            run.hasFailed() ? Optional.of(DeploymentJobs.JobError.unknown) : Optional.empty());
    }

    /** Returns the application package for the tester application, assembled from a generated config, fat-jar and services.xml. */
    private ApplicationPackage testerPackage(RunId id) {
        ApplicationVersion version = application(id.application()).deploymentJobs()
                                                                  .statusOf(id.type()).get()
                                                                  .lastTriggered().get()
                                                                  .application();

        byte[] testPackage = controller.applications().artifacts().getTesterPackage(testerOf(id.application()), version.id());
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
                                             .ifPresent(endpoints -> deployments.put(zone, endpoints)));
        return deployments.build();
    }

    /** Returns a URI of the tester endpoint retrieved from the routing generator, provided it matches an expected form. */
    private Optional<URI> testerEndpoint(RunId id) {
        ApplicationId tester = testerOf(id.application());
        return controller.applications().getDeploymentEndpoints(new DeploymentId(tester, zone(id.type())))
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
            return new ByteArrayLogger(parent, String.format(".%s.%s.%s", id.serializedForm(), type.jobName(), step));
        }

        @Override
        public void log(LogRecord record) {
            // TODO jvenstad: Store log records in a serialised format.
            String timestamp = timestampFormat.format(new Date(record.getMillis()));
            for (String line : record.getMessage().split("\n"))
                out.println(timestamp + ": " + line);

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
