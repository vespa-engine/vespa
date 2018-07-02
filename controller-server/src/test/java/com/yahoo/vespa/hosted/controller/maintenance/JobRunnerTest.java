package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.TestIdentities;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.JobController;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.deployment.Step.Status;
import com.yahoo.vespa.hosted.controller.deployment.StepRunner;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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

        jobs.updateStorage(); // Holding the lock of each job ensures data read below is fresh.
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

    @Test
    public void stepLocking() throws InterruptedException, BrokenBarrierException {
        DeploymentTester tester = new DeploymentTester();
        JobController jobs = tester.controller().jobController();
        // Hang during tester deployment, until notified.
        CyclicBarrier barrier = new CyclicBarrier(2);
        JobRunner runner = new JobRunner(tester.controller(), Duration.ofDays(1), new JobControl(tester.controller().curator()),
                                         Executors.newFixedThreadPool(32), waitingRunner(barrier));

        ApplicationId id = tester.createApplication("real", "tenant", 1, 1L).id();
        jobs.submit(id, new SourceRevision("repo", "branch", "bada55"), new byte[0], new byte[0]);

        RunId runId = new RunId(id, systemTest, 1);
        jobs.run(id, systemTest);
        runner.maintain();
        barrier.await();
        try {
            jobs.locked(id, systemTest, deactivateTester, step -> { });
            fail("deployTester step should still be locked!");
        }
        catch (TimeoutException e) { }

        // Thread is still trying to deploy tester -- delete application, and see all data is garbage collected.
        assertEquals(Collections.singletonList(runId), jobs.active().stream().map(run -> run.id()).collect(Collectors.toList()));
        tester.controller().applications().deleteApplication(id, Optional.of(TestIdentities.userNToken));
        assertEquals(Collections.emptyList(), jobs.active());
        assertEquals(runId, jobs.last(id, systemTest).get().id());

        // Deployment still ongoing, so garbage is not yet collected.
        runner.maintain();
        assertEquals(runId, jobs.last(id, systemTest).get().id());

        // Deployment lets go, deactivation may now run, and trash is thrown out.
        barrier.await();
        runner.maintain();
        assertEquals(Optional.empty(), jobs.last(id, systemTest));
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

    private static StepRunner waitingRunner(CyclicBarrier barrier) {
        return (step, id) -> {
            try {
                if (step.get() == deployTester) {
                    barrier.await(); // Wake up the main thread, which waits for this step to be locked.
                    barrier.reset();
                    barrier.await(); // Then wait while holding the lock for this step, until the main thread wakes us up.
                }
            }
            catch (InterruptedException | BrokenBarrierException e) {
                throw new AssertionError(e);
            }
            return succeeded;
        };
    }

}
