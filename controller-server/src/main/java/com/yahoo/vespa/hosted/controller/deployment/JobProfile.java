package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static com.yahoo.vespa.hosted.controller.deployment.Step.*;

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
                          startTests),
               EnumSet.of(storeData,
                          deactivateTester,
                          deactivateReal)),

    stagingTest(EnumSet.of(deployInitialReal,
                           installInitialReal,
                           deployReal,
                           installReal,
                           deployTester,
                           installTester,
                           startTests),
                EnumSet.of(storeData,
                           deactivateTester,
                           deactivateReal)),

    production(EnumSet.of(deployReal,
                          installReal,
                          deployTester,
                          installTester,
                          startTests),
               EnumSet.of(storeData,
                          deactivateTester));


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
            case prod: return production;
            default: throw new AssertionError("Unexpected environment '" + type.environment() + "'!");
        }
    }

    /** Returns all steps in this profile, the default for which is to run only when all prerequisites are successes. */
    public Set<Step> steps() { return steps; }

    /** Returns the set of steps that should always be run, regardless of outcome. */
    public Set<Step> alwaysRun() { return alwaysRun; }

}
