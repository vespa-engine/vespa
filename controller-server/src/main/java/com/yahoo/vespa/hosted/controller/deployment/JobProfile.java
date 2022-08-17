// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static com.yahoo.vespa.hosted.controller.deployment.Step.copyVespaLogs;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployInitialReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.endStagingSetup;
import static com.yahoo.vespa.hosted.controller.deployment.Step.endTests;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installInitialReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.report;
import static com.yahoo.vespa.hosted.controller.deployment.Step.startStagingSetup;
import static com.yahoo.vespa.hosted.controller.deployment.Step.startTests;

/**
 * Static profiles defining the {@link Step}s of a deployment job.
 *
 * @author jonmv
 */
public enum JobProfile {

    systemTest(EnumSet.of(deployReal,
                          installReal,
                          deployTester,
                          installTester,
                          startTests,
                          endTests,
                          copyVespaLogs,
                          deactivateTester,
                          deactivateReal,
                          report)),

    stagingTest(EnumSet.of(deployInitialReal,
                           deployTester,
                           installTester,
                           installInitialReal,
                           startStagingSetup,
                           endStagingSetup,
                           deployReal,
                           installReal,
                           startTests,
                           endTests,
                           copyVespaLogs,
                           deactivateTester,
                           deactivateReal,
                           report)),

    production(EnumSet.of(deployReal,
                          installReal,
                          report)),

    productionTest(EnumSet.of(deployTester,
                              installTester,
                              startTests,
                              endTests,
                              copyVespaLogs,
                              deactivateTester,
                              report)),

    development(EnumSet.of(deployReal,
                           installReal,
                           copyVespaLogs)),

    developmentDryRun(EnumSet.of(deployReal));


    private final Set<Step> steps;

    JobProfile(Set<Step> steps) {
        this.steps = Collections.unmodifiableSet(steps);
    }

    // TODO jonmv: Let caller decide profile, and store with run?
    public static JobProfile of(JobType type) {
        switch (type.environment()) {
            case test: return systemTest;
            case staging: return stagingTest;
            case prod: return type.isTest() ? productionTest : production;
            case perf:
            case dev: return development;
            default: throw new AssertionError("Unexpected environment '" + type.environment() + "'!");
        }
    }

    /** Returns all steps in this profile, the default for which is to run only when all prerequisites are successes. */
    public Set<Step> steps() { return steps; }

}
