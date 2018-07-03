package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableMap;
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
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.deployment.Step.Status;

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
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.failed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Runs steps of a deployment job against its provided controller.
 *
 * A dual-purpose logger is set up for each thread that runs a step here:
 *   1. All messages are logged to a buffer which is stored in an external log storage at the end of execution, and
 *   2. all messages are also logged through the usual logging framework; thus, by default, any messages of level
 *      {@code Level.INFO} or higher end up in the Vespa log, and all messages may be sent there by means of log-control.
 *
 * @author jonmv
 */
public class InternalStepRunner implements StepRunner {

    static final Duration endpointTimeout = Duration.ofMinutes(15);

    // TODO jvenstad: Move this tester logic to the application controller, perhaps?
    public static ApplicationId testerOf(ApplicationId id) {
        return ApplicationId.from(id.tenant().value(),
                                  id.application().value(),
                                  id.instance().value() + "-t");
    }

    private final Controller controller;
    // Wraps loggers which additionally write all records to byte arrays which are stored as the deployment job logs.
    private final ThreadLocal<ByteArrayLogger> logger = new ThreadLocal<>();

    public InternalStepRunner(Controller controller) {
        this.controller = controller;
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
        finally {
            controller.jobController().log(id, step.get(), logger.get().getLog());
            logger.remove();
        }
    }

    private Status deployInitialReal(RunId id) {
        logger.get().log(DEBUG, "Deploying the current version of " + id.application() + " ...");
        return deployReal(id, true);
    }

    private Status deployReal(RunId id) {
        logger.get().log(DEBUG, "Deploying the version to test of " + id.application() + " ...");
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
        logger.get().log(DEBUG, "Attempting to find endpoints for " + id + " ...");
        Map<ZoneId, List<URI>> endpoints = deploymentEndpoints(id.application());
        logger.get().log(DEBUG, "Found endpoints:\n" +
                                endpoints.entrySet().stream()
                                         .map(zoneEndpoints -> "- " + zoneEndpoints.getKey() + ":\n" +
                                                               zoneEndpoints.getValue().stream()
                                                                            .map(uri -> " |-- " + uri)
                                                                            .collect(Collectors.joining("\n"))));
        if ( ! endpoints.containsKey(zone(id.type()))) {
            if (application(id.application()).deployments().get(zone(id.type())).at()
                                             .isBefore(controller.clock().instant().minus(endpointTimeout))) {
                logger.get().log(WARNING, "Endpoints for " + id.application() + " in " + zone(id.type()) +
                                          " failed to show up within " + endpointTimeout.toMinutes() + " minutes!");
                return failed;
            }

            logger.get().log(DEBUG, "Endpoints for the deployment to test are not yet ready.");
            return unfinished;
        }

        logger.get().log(DEBUG, "Deploying the tester container for " + id.application() + " ...");
        return deploy(testerOf(id.application()),
                      id.type(),
                      () -> controller.applications().deployTester(testerOf(id.application()),
                                                                   testerPackage(id, endpoints),
                                                                   zone(id.type()),
                                                                   new DeployOptions(true,
                                                                                     Optional.of(controller.systemVersion()),
                                                                                     false,
                                                                                     false)));
    }

    private Status deploy(ApplicationId id, JobType type, Supplier<ActivateResult> deployment) {
        try {
            // TODO jvenstad: Do whatever is required based on the result, and log all of this.
            PrepareResponse prepareResponse = deployment.get().prepareResponse();
            if ( ! prepareResponse.configChangeActions.refeedActions.stream().allMatch(action -> action.allowed)) {
                logger.get().log(DEBUG, "Deploy failed due to non-compatible changes that require re-feed. " +
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
                logger.get().log(DEBUG, "No services requiring restart.");
            else
                prepareResponse.configChangeActions.restartActions.stream()
                                                                  .flatMap(action -> action.services.stream())
                                                                  .map(service -> service.hostName)
                                                                  .sorted().distinct()
                                                                  .map(Hostname::new)
                                                                  .forEach(hostname -> {
                                                                      controller.applications().restart(new DeploymentId(id, zone(type)), Optional.of(hostname));
                                                                      logger.get().log(DEBUG, "Restarting services on host " + hostname.id() + ".");
                                                                  });
            logger.get().log(DEBUG, "Deployment of " + id + " in " + zone(type) + " was successful!");
            return succeeded;
        }
        catch (ConfigServerException e) {
            if (   e.getErrorCode() == OUT_OF_CAPACITY && type.isTest()
                || e.getErrorCode() == ACTIVATION_CONFLICT
                || e.getErrorCode() == APPLICATION_LOCK_FAILURE) {
                logger.get().log(DEBUG, "Exception of type '" + e.getErrorCode() + "' attempting to deploy:\n" +
                                        e.getMessage() + "\n");
                return unfinished;
            }

            logger.get().log(INFO, "Exception of type '" + e.getErrorCode() + "' attempting to deploy:\n" +
                                   e.getMessage() + "\n");
            return failed;
        }
    }

    private Status installInitialReal(RunId id) {
        return install(id.application(), id.type());
    }

    private Status installReal(RunId id) {
        return install(id.application(), id.type());
    }

    private Status installTester(RunId id) {
        return install(testerOf(id.application()), id.type());
    }

    private Status install(ApplicationId id, JobType type) {
        // If converged and serviceconverged: succeeded
        // If timeout, failed
        return unfinished;
    }

    private Status startTests(RunId id) {
        // Empty for now, but will be: find endpoints and post them.
        throw new AssertionError();
    }

    private Status endTests(RunId id) {
        // Update test logs.
        // If tests are done, return test results.
        throw new AssertionError();
    }

    private Status deactivateReal(RunId id) {
        return deactivate(id.application(), id.type());
    }

    private Status deactivateTester(RunId id) {
        return deactivate(testerOf(id.application()), id.type());
    }

    private Status deactivate(ApplicationId id, JobType type) {
        // Try to deactivate, and if deactivated, finished.
        throw new AssertionError();
    }

    private Status report(RunId id) {
        // Easy squeezy.
        throw new AssertionError();
    }

    private Application application(ApplicationId id) {
        return controller.applications().require(id);
    }

    private ZoneId zone(JobType type) {
        return type.zone(controller.system()).get();
    }

    private ApplicationPackage testerPackage(RunId id, Map<ZoneId, List<URI>> endpoints) {
        ApplicationVersion version = application(id.application()).deploymentJobs()
                                                                  .statusOf(id.type()).get()
                                                                  .lastTriggered().get()
                                                                  .application();

        byte[] testConfig = testConfig(id.application(), zone(id.type()), controller.system(), endpoints);
        byte[] testJar = controller.applications().artifacts().getTesterJar(testerOf(id.application()), version.id());
        byte[] servicesXml = servicesXml();

        // TODO hakonhall: Assemble!

        throw new AssertionError();
    }

    private Map<ZoneId, List<URI>> deploymentEndpoints(ApplicationId id) {
        ImmutableMap.Builder<ZoneId, List<URI>> deployments = ImmutableMap.builder();
        application(id).deployments().keySet()
                  .forEach(zone -> controller.applications().getDeploymentEndpoints(new DeploymentId(id, zone))
                                             .ifPresent(endpoints -> deployments.put(zone, endpoints)));
        return deployments.build();
    }

    private byte[] servicesXml() {
        //TODO hakonhall: Create!
        return "".getBytes();
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
            String timestamp = timestampFormat.format(new Date(record.getMillis()));
            for (String line : record.getMessage().split("\n"))
                out.println(timestamp + ": " + line);

            getParent().log(record);
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
