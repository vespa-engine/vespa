// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Comparator.reverseOrder;

/**
 * Steps that make up a deployment job. See {@link JobProfile} for preset profiles.
 *
 * Each step lists its prerequisites; this serves two purposes:
 *
 *   1. A step may only run after its prerequisites, so these define a topological order in which
 *      the steps can be run. Since a job profile may list only a subset of the existing steps,
 *      only the prerequisites of a step which are included in a run's profile will be considered.
 *      Under normal circumstances, a step will run only after each of its prerequisites have succeeded.
 *      When a run has failed, however, each of the always-run steps of the run's profile will be run,
 *      again in a topological order, and requiring all their always-run prerequisites to have run.
 *
 *   2. A step will never run concurrently with its prerequisites. This is to ensure, e.g., that relevant
 *      information from a failed run is stored, and that deployment does not occur after deactivation.
 *
 * @see JobController
 * @author jonmv
 */
public enum Step {

    /** Download test-jar and assemble and deploy tester application. */
    deployTester(false),

    /** See that tester is done deploying, and is ready to serve. */
    installTester(false, deployTester),

    /** Download and deploy the initial real application, for staging tests. */
    deployInitialReal(false),

    /** See that the real application has had its nodes converge to the initial state. */
    installInitialReal(false, deployInitialReal),

    /** Ask the tester to run its staging setup. */
    startStagingSetup(false, installInitialReal, installTester),

    /** See that the staging setup is done. */
    endStagingSetup(false, startStagingSetup),

    /** Download and deploy real application, restarting services if required. */
    deployReal(false, endStagingSetup),

    /** See that real application has had its nodes converge to the wanted version and generation. */
    installReal(false, deployReal),

    /** Ask the tester to run its tests. */
    startTests(false, installReal, installTester),

    /** See that the tests are done running. */
    endTests(false, startTests),

    /** Fetch and store Vespa logs from the log server cluster of the deployment -- used for test and dev deployments. */
    copyVespaLogs(true, installReal, endTests),

    /** Delete the real application -- used for test deployments. */
    deactivateReal(true, deployInitialReal, deployReal, endTests, copyVespaLogs),

    /** Deactivate the tester. */
    deactivateTester(true, deployTester, endTests, copyVespaLogs),

    /** Report completion to the deployment orchestration machinery. */
    report(true, installReal, deactivateReal, deactivateTester);


    private final boolean alwaysRun;
    private final List<Step> prerequisites;

    Step(boolean alwaysRun, Step... prerequisites) {
        this.alwaysRun = alwaysRun;
        this.prerequisites = List.of(prerequisites);
    }

    /** Returns whether this is a cleanup-step, and should always run, regardless of job outcome, when specified in a job. */
    public boolean alwaysRun() { return alwaysRun; }

    /** Returns all prerequisite steps for this, including transient ones, in a job profile containing the given steps. */
    public List<Step> allPrerequisites(Collection<Step> among) {
        return prerequisites.stream()
                            .filter(among::contains)
                            .flatMap(pre -> Stream.concat(Stream.of(pre),
                                                          pre.allPrerequisites(among).stream()))
                            .sorted(reverseOrder())
                            .distinct()
                            .toList();
    }


    /** Returns the direct prerequisite steps that must be completed before this, assuming the job contains these steps. */
    public List<Step> prerequisites() { return prerequisites; }


    public enum Status {

        /** Step still has unsatisfied finish criteria -- it may not even have started. */
        unfinished,

        /** Step failed and subsequent steps may not start. */
        failed,

        /** Step succeeded and subsequent steps may now start. */
        succeeded;

        public static Step.Status of(RunStatus status) {
            switch (status) {
                case success : throw new AssertionError("Unexpected run status '" + status + "'!");
                case reset   :
                case aborted : return unfinished;
                case noTests :
                case running : return succeeded;
                default      : return failed;
            }
        }

    }

}
