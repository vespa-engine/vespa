// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import static com.yahoo.vespa.hosted.controller.deployment.Step.endTests;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installInitialReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.report;
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
                          endTests),
               EnumSet.of(copyVespaLogs,
                          deactivateTester,
                          deactivateReal,
                          report)),

    stagingTest(EnumSet.of(deployInitialReal,
                           installInitialReal,
                           deployReal,
                           installReal,
                           deployTester,
                           installTester,
                           startTests,
                           endTests),
                EnumSet.of(copyVespaLogs,
                           deactivateTester,
                           deactivateReal,
                           report)),

    production(EnumSet.of(deployReal,
                          installReal,
                          deployTester,
                          installTester,
                          startTests,
                          endTests),
               EnumSet.of(deactivateTester,
                          report)),

    productionTest(EnumSet.of(deployTester,
                              installTester,
                              startTests,
                              endTests),
                   EnumSet.of(deactivateTester,
                              report)),

    development(EnumSet.of(deployReal,
                           installReal),
                EnumSet.of(copyVespaLogs));


    private final Set<Step> steps;
    private final Set<Step> alwaysRun;

    JobProfile(Set<Step> runWhileSuccess, Set<Step> alwaysRun) {
        runWhileSuccess.addAll(alwaysRun);
        this.steps = Collections.unmodifiableSet(runWhileSuccess);
        this.alwaysRun = Collections.unmodifiableSet(alwaysRun);
    }

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

    /** Returns the set of steps that should always be run, regardless of outcome. */
    public Set<Step> alwaysRun() { return alwaysRun; }

}
