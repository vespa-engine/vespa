package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

public class DummyStepRunner implements StepRunner {

    @Override
    public Step.Status run(LockedStep step, RunId id) {
        return Step.Status.succeeded;
    }

}
