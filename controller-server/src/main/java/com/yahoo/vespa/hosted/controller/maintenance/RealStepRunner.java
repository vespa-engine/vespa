package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.ActivateResult;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.deployment.LockedStep;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step;

import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.failed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;

/**
 * Runs steps of a deployment job against its provided controller.
 *
 * @author jonmv
 */
public class RealStepRunner implements StepRunner {

    private static ApplicationId testerOf(ApplicationId id) {
        return ApplicationId.from(id.tenant().value(),
                                  id.application().value(),
                                  "-test-" + id.instance().value());
    }

    private final Controller controller;

    public RealStepRunner(Controller controller) {
        this.controller = controller;
    }

    @Override
    public Step.Status run(LockedStep step, RunStatus run) {
        RunId id = run.id();
        switch (step.get()) {
            case deployInitialReal: return deployInitialReal(id);
            case installInitialReal: return installInitialReal(id);
            case deployReal: return deployReal(id);
            case deployTester: return deployTester(id);
            case installReal: return installReal(id);
            case installTester: return installTester(id);
            case startTests: return startTests(id);
            case storeData: return storeData(id);
            case deactivateReal: return deactivateReal(id);
            case deactivateTester: return deactivateTester(id);
            case report: return report(id);
            default: throw new AssertionError("Unknown step '" + step + "'!");
        }
    }

    private Step.Status deployInitialReal(RunId id) {
        return deployReal(id, true);
    }

    private Step.Status installInitialReal(RunId id) {
        // If converged and serviceconverged: succeeded
        // If timeout, failed
        return unfinished;
    }

    private Step.Status deployReal(RunId id) {
        // Separate out deploy logic from above, and reuse.
        return deployReal(id,false);
    }

    private Step.Status deployTester(RunId id) {
        // Find endpoints of real application. This will move down at a later time.
        // See above.
        throw new AssertionError();
    }

    private Step.Status installReal(RunId id) {
        // See three above.
        throw new AssertionError();
    }

    private Step.Status installTester(RunId id) {
        // See above.
        throw new AssertionError();
    }

    private Step.Status startTests(RunId id) {
        // Empty for now, but will be: find endpoints and post them.
        throw new AssertionError();
    }

    private Step.Status storeData(RunId id) {
        // Update test logs.
        // If tests are done, return test results.
        throw new AssertionError();
    }

    private Step.Status deactivateReal(RunId id) {
        // Try to deactivate, and if deactivated, finished.
        throw new AssertionError();
    }

    private Step.Status deactivateTester(RunId id) {
        // See above.
        throw new AssertionError();
    }

    private Step.Status report(RunId id) {
        // Easy squeezy.
        throw new AssertionError();
    }

    private Step.Status deployReal(RunId id, boolean setTheStage) {
        try {
            // TODO jvenstad: Do whatever is required based on the result, and log all of this.
            ActivateResult result = controller.applications().deploy(id.application(),
                                                                     id.type().zone(controller.system()).get(),
                                                                     Optional.empty(),
                                                                     new DeployOptions(false,
                                                                                       Optional.empty(),
                                                                                       false,
                                                                                       setTheStage));
            return succeeded;
        }
        catch (ConfigServerException e) {
            // TODO jvenstad: Consider retrying different things as well.
            // TODO jvenstad: Log error information.
            if (id.type().isTest() && e.getErrorCode() == ConfigServerException.ErrorCode.OUT_OF_CAPACITY)
                return unfinished;
        }
        return failed;
    }

    private Application application(ApplicationId id) {
        return controller.applications().require(id);
    }

}
