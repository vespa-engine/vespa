package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.JobController;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.deployment.Step.Status;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.failed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installInitialReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.report;
import static com.yahoo.vespa.hosted.controller.deployment.Step.startTests;
import static com.yahoo.vespa.hosted.controller.deployment.Step.endTests;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author jonmv
 */
public class JobRunnerTest {

    @Test
    public void multiThreadedExecutionFinishes() throws InterruptedException {
        DeploymentTester tester = new DeploymentTester();
        JobController jobs = tester.controller().jobController();
        // Fail the installation of the initial version of the real application in staging tests, and succeed everything else.
        StepRunner stepRunner = (step, id) -> id.type() == stagingTest && step.get() == installInitialReal ? failed : succeeded;
        CountDownLatch latch = new CountDownLatch(14); // Number of steps that will run, below.
        JobRunner runner = new JobRunner(tester.controller(), Duration.ofDays(1), new JobControl(tester.controller().curator()),
                                         Executors.newFixedThreadPool(32), notifying(stepRunner, latch));

        ApplicationId id = tester.createApplication("real", "tenant", 1, 1L).id();
        jobs.submit(id, new SourceRevision("repo", "branch", "bada55"), new byte[0], new byte[0]);

        jobs.run(id, systemTest);
        try {
            jobs.run(id, systemTest);
            fail("Job is already running, so this should not be allowed!");
        }
        catch (IllegalStateException e) { }
        jobs.run(id, stagingTest);

        assertTrue(jobs.last(id, systemTest).get().steps().values().stream().allMatch(unfinished::equals));
        runner.maintain();
        assertFalse(jobs.last(id, systemTest).get().hasEnded());
        assertFalse(jobs.last(id, stagingTest).get().hasEnded());

        latch.await(1, TimeUnit.SECONDS);
        assertEquals(0, latch.getCount());

        jobs.active().forEach(run -> jobs.active(run.id())); // Wait for locks of jobs which haven't yet been written through.
        assertTrue(jobs.last(id, systemTest).get().steps().values().stream().allMatch(succeeded::equals));
        assertTrue(jobs.last(id, stagingTest).get().hasEnded());
        assertTrue(jobs.last(id, stagingTest).get().hasFailed());
    }

    @Test
    public void stepLogic() {
        DeploymentTester tester = new DeploymentTester();
        JobController jobs = tester.controller().jobController();
        Map<Step, Status> outcomes = new EnumMap<>(Step.class);
        JobRunner runner = new JobRunner(tester.controller(), Duration.ofDays(1), new JobControl(tester.controller().curator()),
                                         inThreadExecutor(), mappedRunner(outcomes));

        ApplicationId id = tester.createApplication("real", "tenant", 1, 1L).id();
        jobs.submit(id, new SourceRevision("repo", "branch", "bada55"), new byte[0], new byte[0]);
        Supplier<RunStatus> run = () -> jobs.last(id, systemTest).get();

        jobs.run(id, systemTest);
        RunId first = run.get().id();

        Map<Step, Status> steps = run.get().steps();
        runner.maintain();
        assertEquals(steps, run.get().steps());
        assertEquals(Arrays.asList(deployReal), run.get().readySteps());

        outcomes.put(deployReal, succeeded);
        runner.maintain();
        assertEquals(Arrays.asList(installReal), run.get().readySteps());

        outcomes.put(installReal, succeeded);
        runner.maintain();
        assertEquals(Arrays.asList(deployTester), run.get().readySteps());

        outcomes.put(deployTester, succeeded);
        runner.maintain();
        assertEquals(Arrays.asList(installTester), run.get().readySteps());

        outcomes.put(installTester, succeeded);
        runner.maintain();
        assertEquals(Arrays.asList(startTests), run.get().readySteps());

        outcomes.put(startTests, succeeded);
        runner.maintain();
        assertEquals(Arrays.asList(endTests), run.get().readySteps());

        outcomes.put(endTests, succeeded);
        runner.maintain();
        assertEquals(Arrays.asList(deactivateReal, deactivateTester), run.get().readySteps());

        // Failure deactivating real fails the run, but run-always steps continue.
        outcomes.put(deactivateReal, failed);
        runner.maintain();
        assertTrue(run.get().hasFailed());
        assertEquals(Arrays.asList(deactivateReal, deactivateTester), run.get().readySteps());

        // Abortion does nothing, as the run has already failed.
        jobs.abort(run.get().id());
        runner.maintain();
        assertEquals(Arrays.asList(deactivateReal, deactivateTester), run.get().readySteps());

        outcomes.put(deactivateReal, succeeded);
        outcomes.put(deactivateTester, succeeded);
        outcomes.put(report, succeeded);
        runner.maintain();
        assertTrue(run.get().hasFailed());
        assertTrue(run.get().hasEnded());
        assertTrue(run.get().isAborted());

        // A new run is attempted.
        jobs.run(id, systemTest);
        assertEquals(first.number() + 1, run.get().id().number());

        // Run fails on tester deployment -- remaining run-always steps succeed, and the run finishes.
        outcomes.put(deployTester, failed);
        runner.maintain();
        assertTrue(run.get().hasEnded());
        assertTrue(run.get().hasFailed());
        assertFalse(run.get().isAborted());
        assertEquals(failed, run.get().steps().get(deployTester));
        assertEquals(unfinished, run.get().steps().get(installTester));
        assertEquals(succeeded, run.get().steps().get(report));
    }

    private static ExecutorService inThreadExecutor() {
        return new AbstractExecutorService() {
            AtomicBoolean shutDown = new AtomicBoolean(false);
            @Override public void shutdown() { shutDown.set(true); }
            @Override public List<Runnable> shutdownNow() { shutDown.set(true); return Collections.emptyList(); }
            @Override public boolean isShutdown() { return shutDown.get(); }
            @Override public boolean isTerminated() { return shutDown.get(); }
            @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
            @Override public void execute(Runnable command) { command.run(); }
        };
    }

    private static StepRunner notifying(StepRunner runner, CountDownLatch latch) {
        return (step, id) -> {
            Status status = runner.run(step, id);
            synchronized (latch) {
                assertTrue(latch.getCount() > 0);
                latch.countDown();
            }
            return status;
        };
    }

    private static StepRunner mappedRunner(Map<Step, Status> outcomes) {
        return (step, id) -> outcomes.getOrDefault(step.get(), Status.unfinished);
    }

}
