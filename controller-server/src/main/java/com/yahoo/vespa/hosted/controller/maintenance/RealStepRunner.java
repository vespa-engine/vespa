package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.deployment.LockedStep;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step;

public class RealStepRunner implements StepRunner {

    private static final String prefix = "-test-";

    private final ApplicationController applications;

    public RealStepRunner(ApplicationController applications) {
        this.applications = applications;
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
            default: throw new AssertionError("Unknown step '" + step + "'!");
        }
    }

    private Step.Status deployInitialReal(RunId id) {

    }

    private Step.Status installInitialReal(RunId id) {
        throw new AssertionError();
    }

    private Step.Status deployReal(RunId id) {
        throw new AssertionError();
    }

    private Step.Status deployTester(RunId id) {
        throw new AssertionError();
    }

    private Step.Status installReal(RunId id) {
        throw new AssertionError();
    }

    private Step.Status installTester(RunId id) {
        throw new AssertionError();
    }

    private Step.Status startTests(RunId id) {
        throw new AssertionError();
    }

    private Step.Status storeData(RunId id) {
        throw new AssertionError();
    }

    private Step.Status deactivateReal(RunId id) {
        throw new AssertionError();
    }

    private Step.Status deactivateTester(RunId id) {
        throw new AssertionError();
    }

    private static ApplicationId testerOf(ApplicationId id) {
        return ApplicationId.from(id.tenant().value(),
                                  id.application().value(),
                                  prefix + id.instance().value());
    }

}
