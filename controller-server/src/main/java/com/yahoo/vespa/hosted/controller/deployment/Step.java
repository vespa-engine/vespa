package com.yahoo.vespa.hosted.controller.deployment;

import java.util.Arrays;
import java.util.List;

public enum Step {

    /** Download and deploy the initial real application, for staging tests. */
    deployInitialReal,

    /** See that the real application has had its nodes converge to the initial state. */
    installInitialReal(deployInitialReal),

    /** Download and deploy real application, restarting services if required. */
    deployReal(installInitialReal),

    /** See that real application has had its nodes converge to the wanted version and generation. */
    installReal(deployReal),

    /** Find test endpoints, download test-jar, and assemble and deploy tester application. */
    deployTester(deployReal),

    /** See that tester is done deploying, and is ready to serve. */
    installTester(deployTester),

    /** Ask the tester to run its tests. */
    runTests(installReal, installTester),

    /** Download data from the tester, and store it. */
    storeData(runTests),

    /** Deactivate the tester, and the real deployment if test or staging environment. */
    tearDown(storeData);


    private final List<Step> prerequisites;

    Step(Step... prerequisites) {
        this.prerequisites = Arrays.asList(prerequisites);
        // Hmm ... Need to pick out only the relevant prerequisites, and to allow storeData and tearDown to always run.
    }


    public enum Profile {

        systemTest(deployReal, installReal, deployTester, installTester, runTests),

        stagingTest,

        productionTest;


        private final List<Step> steps;

        Profile(Step... steps) {
            this.steps = Arrays.asList(steps);
        }

    }


    public enum Status {

        /** Step is waiting for its prerequisites to succeed. */
        pending,

        /** Step is currently running. */
        running,

        /** Step failed, and subsequent steps can not start. */
        failed,

        /** Step succeeded, and subsequent steps may not start. */
        succeeded;

    }

}
