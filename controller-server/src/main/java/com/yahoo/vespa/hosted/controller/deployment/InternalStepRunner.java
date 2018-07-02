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
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.deployment.Step.Status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException.ErrorCode.ACTIVATION_CONFLICT;
import static com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException.ErrorCode.APPLICATION_LOCK_FAILURE;
import static com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException.ErrorCode.OUT_OF_CAPACITY;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.failed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;

/**
 * Runs steps of a deployment job against its provided controller.
 *
 * @author jonmv
 */
public class InternalStepRunner implements StepRunner {

    // TODO jvenstad: Move this tester logic to the application controller, perhaps?
    public static ApplicationId testerOf(ApplicationId id) {
        return ApplicationId.from(id.tenant().value(),
                                  id.application().value(),
                                  id.instance().value() + "-t");
    }

    private final Controller controller;

    public InternalStepRunner(Controller controller) {
        this.controller = controller;
    }

    @Override
    public Status run(LockedStep step, RunId id) {
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

    private Status deployInitialReal(RunId id) {
        return deployReal(id, true);
    }

    private Status deployReal(RunId id) {
        return deployReal(id, false);
    }

    private Status deployReal(RunId id, boolean setTheStage) {
        return deploy(id.application(),
                      id.type(),
                      () -> controller.applications().deploy(id.application(),
                                                             id.type().zone(controller.system()).get(),
                                                             Optional.empty(),
                                                             new DeployOptions(false,
                                                                               Optional.empty(),
                                                                               false,
                                                                               setTheStage)));
    }

    private Status deployTester(RunId id) {
        Map<ZoneId, List<URI>> endpoints = deploymentEndpoints(id.application());
        if ( ! endpoints.containsKey(id.type().zone(controller.system()).get()))
            return unfinished;

        return deploy(testerOf(id.application()),
                      id.type(),
                      () -> controller.applications().deployTester(testerOf(id.application()),
                                                                   testerPackage(id, endpoints),
                                                                   id.type().zone(controller.system()).get(),
                                                                   new DeployOptions(true,
                                                                                     Optional.of(controller.systemVersion()),
                                                                                     false,
                                                                                     false)));
    }

    private Status deploy(ApplicationId id, JobType type, Supplier<ActivateResult> deploy) {
        try {
            // TODO jvenstad: Do whatever is required based on the result, and log all of this.
            ActivateResult result = deploy.get();

            return succeeded;
        }
        catch (ConfigServerException e) {
            // TODO jvenstad: Consider retrying different things as well.
            // TODO jvenstad: Log error information.
            if (   e.getErrorCode() == OUT_OF_CAPACITY && type.isTest()
                || e.getErrorCode() == ACTIVATION_CONFLICT
                || e.getErrorCode() == APPLICATION_LOCK_FAILURE) {

                return unfinished;
            }
        }
        return failed;
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

    private ApplicationPackage testerPackage(RunId id, Map<ZoneId, List<URI>> endpoints) {
        ApplicationVersion version = application(id.application()).deploymentJobs()
                                                                  .statusOf(id.type()).get()
                                                                  .lastTriggered().get()
                                                                  .application();

        byte[] testConfig = testConfig(id.application(), id.type().zone(controller.system()).get(), controller.system(), endpoints);
        byte[] testJar = controller.applications().artifacts().getTesterJar(testerOf(id.application()), version.id());
        byte[] servicesXml = servicesXml();

        // TODO hakonhall: Assemble!

        throw new AssertionError();
    }

    private Map<ZoneId, List<URI>> deploymentEndpoints(ApplicationId id) {
        ImmutableMap.Builder<ZoneId, List<URI>> deployments = ImmutableMap.builder();
        controller.applications().require(id).deployments().keySet()
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

}
