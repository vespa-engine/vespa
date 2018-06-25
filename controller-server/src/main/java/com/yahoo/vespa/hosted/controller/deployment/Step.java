package com.yahoo.vespa.hosted.controller.deployment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Steps that make up a deployment job. See {@link JobProfile} for preset profiles.
 *
 * @author jonmv
 */
public enum Step {

    /** Download and deploy the initial real application, for staging tests. */
    deployInitialReal,

    /** See that the real application has had its nodes converge to the initial state. */
    installInitialReal(deployInitialReal),

    /** Download and deploy real application, restarting services if required. */
    deployReal(installInitialReal),

    /** Find test endpoints, download test-jar, and assemble and deploy tester application. */
    deployTester(deployReal), // TODO jvenstad: Move this up when config can be POSTed.

    /** See that real application has had its nodes converge to the wanted version and generation. */
    installReal(deployReal),

    /** See that tester is done deploying, and is ready to serve. */
    installTester(deployTester),

    /** Ask the tester to run its tests. */
    runTests(installReal, installTester),

    /** Download data from the tester and store it. */
    storeData(runTests),

    /** Delete the real application -- used for test deployments. */
    deactivateReal(deployInitialReal, deployReal, runTests),

    /** Deactivate the tester. */
    deactivateTester(deployTester, storeData);


    private final List<Step> prerequisites;

    Step(Step... prerequisites) {
        this.prerequisites = Collections.unmodifiableList(Arrays.asList(prerequisites));
    }

    public List<Step> prerequisites() { return prerequisites; }


    public enum Status {

        /** Step is waiting for its prerequisites to succeed, or is running. */
        unfinished,

        /** Step failed, and subsequent steps can not start. */
        failed,

        /** Step succeeded, and subsequent steps may not start. */
        succeeded;

    }

}
