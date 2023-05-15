// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.JobController;
import com.yahoo.vespa.hosted.controller.deployment.JobMetrics;
import com.yahoo.vespa.hosted.controller.deployment.JobProfile;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.deployment.Step.Status;
import com.yahoo.vespa.hosted.controller.deployment.StepRunner;
import com.yahoo.vespa.hosted.controller.deployment.Submission;
import com.yahoo.vespa.hosted.controller.deployment.Versions;
import com.yahoo.vespa.hosted.controller.integration.MetricsMock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.stagingTest;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.systemTest;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.aborted;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.error;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.reset;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.running;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.success;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.testFailure;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.failed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static com.yahoo.vespa.hosted.controller.deployment.Step.copyVespaLogs;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.endTests;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.report;
import static com.yahoo.vespa.hosted.controller.deployment.Step.startTests;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author jonmv
 */
public class JobRunnerTest {

    private static final ApplicationPackage applicationPackage = new ApplicationPackage(new byte[0]);
    private static final Versions versions = new Versions(Version.fromString("1.2.3"),
                                                          RevisionId.forProduction(321),
                                                          Optional.empty(),
                                                          Optional.empty());

    @Test
    void multiThreadedExecutionFinishes() {
        DeploymentTester tester = new DeploymentTester();
        JobController jobs = tester.controller().jobController();
        StepRunner stepRunner = (step, id) -> id.type().equals(stagingTest) && step.get() == startTests ? Optional.of(error) : Optional.of(running);
        Phaser phaser = new Phaser(1);
        JobRunner runner = new JobRunner(tester.controller(), Duration.ofDays(1), phasedExecutor(phaser), stepRunner);

        TenantAndApplicationId appId = tester.createApplication("tenant", "real", "default").id();
        ApplicationId id = appId.defaultInstance();
        byte[] testPackageBytes = new byte[0];
        jobs.submit(appId, submission(applicationPackage, testPackageBytes), 2);
        start(jobs, id, systemTest);
        try {
            start(jobs, id, systemTest);
            fail("Job is already running, so this should not be allowed!");
        }
        catch (IllegalArgumentException ignored) {
        }
        start(jobs, id, stagingTest);

        assertTrue(jobs.last(id, systemTest).get().stepStatuses().values().stream().allMatch(unfinished::equals));
        assertFalse(jobs.last(id, systemTest).get().hasEnded());
        assertTrue(jobs.last(id, stagingTest).get().stepStatuses().values().stream().allMatch(unfinished::equals));
        assertFalse(jobs.last(id, stagingTest).get().hasEnded());

        runner.maintain();
        phaser.arriveAndAwaitAdvance();
        assertTrue(jobs.last(id, systemTest).get().stepStatuses().values().stream().allMatch(succeeded::equals));
        assertTrue(jobs.last(id, stagingTest).get().hasFailed());

        runner.maintain();
        phaser.arriveAndAwaitAdvance();
        assertTrue(jobs.last(id, systemTest).get().hasEnded());
        assertTrue(jobs.last(id, stagingTest).get().hasEnded());
    }

    @Test
    void stepLogic() {
        DeploymentTester tester = new DeploymentTester();
        JobController jobs = tester.controller().jobController();
        Map<Step, RunStatus> outcomes = new EnumMap<>(Step.class);
        JobRunner runner = new JobRunner(tester.controller(), Duration.ofDays(1), inThreadExecutor(), mappedRunner(outcomes));

        TenantAndApplicationId appId = tester.createApplication("tenant", "real", "default").id();
        ApplicationId id = appId.defaultInstance();
        byte[] testPackageBytes = new byte[0];
        jobs.submit(appId, submission(applicationPackage, testPackageBytes), 2);
        Supplier<Run> run = () -> jobs.last(id, systemTest).get();

        start(jobs, id, systemTest);
        RunId first = run.get().id();

        Map<Step, Status> steps = run.get().stepStatuses();
        runner.maintain();
        assertEquals(steps, run.get().stepStatuses());
        assertEquals(List.of(deployTester, deployReal), run.get().readySteps());
        assertStepsWithStartTime(run.get(), deployTester, deployReal);

        outcomes.put(deployTester, running);
        runner.maintain();
        assertEquals(List.of(installTester, deployReal), run.get().readySteps());
        assertStepsWithStartTime(run.get(), installTester, deployTester, deployReal);

        outcomes.put(deployReal, running);
        runner.maintain();
        assertEquals(List.of(installTester, installReal), run.get().readySteps());
        assertStepsWithStartTime(run.get(), deployTester, deployReal, installTester, installReal);

        outcomes.put(installReal, running);
        runner.maintain();
        assertEquals(List.of(installTester), run.get().readySteps());
        assertStepsWithStartTime(run.get(), deployTester, deployReal, installTester, installReal);

        outcomes.put(installTester, running);
        runner.maintain();
        assertEquals(List.of(startTests), run.get().readySteps());
        assertStepsWithStartTime(run.get(), deployTester, deployReal, installTester, installReal, startTests);

        outcomes.put(startTests, running);
        runner.maintain();
        assertEquals(List.of(endTests), run.get().readySteps());
        assertStepsWithStartTime(run.get(), deployTester, deployReal, installTester, installReal, startTests, endTests);

        // Failure ending tests fails the run, but run-always steps continue.
        outcomes.put(endTests, testFailure);
        runner.maintain();
        assertTrue(run.get().hasFailed());
        assertEquals(List.of(copyVespaLogs), run.get().readySteps());
        assertStepsWithStartTime(run.get(), deployTester, deployReal, installTester, installReal, startTests, endTests, copyVespaLogs);

        outcomes.put(copyVespaLogs, running);
        runner.maintain();
        assertEquals(List.of(deactivateReal, deactivateTester), run.get().readySteps());
        assertStepsWithStartTime(run.get(), deployTester, deployReal, installTester, installReal, startTests, endTests, copyVespaLogs, deactivateTester, deactivateReal);

        // Abortion does nothing, as the run has already failed.
        jobs.abort(run.get().id(), "abort", false);
        runner.maintain();
        assertEquals(List.of(deactivateReal, deactivateTester), run.get().readySteps());
        assertStepsWithStartTime(run.get(), deployTester, deployReal, installTester, installReal, startTests, endTests, copyVespaLogs, deactivateTester, deactivateReal);

        outcomes.put(deactivateReal, running);
        outcomes.put(deactivateTester, running);
        outcomes.put(report, running);
        runner.maintain();
        assertTrue(run.get().hasFailed());
        assertTrue(run.get().hasEnded());
        assertSame(aborted, run.get().status());

        // A new run is attempted.
        start(jobs, id, systemTest);
        assertEquals(first.number() + 1, run.get().id().number());

        // Run fails on tester deployment -- remaining run-always steps succeed, and the run finishes.
        outcomes.put(deployTester, error);
        runner.maintain();
        assertTrue(run.get().hasEnded());
        assertTrue(run.get().hasFailed());
        assertNotSame(aborted, run.get().status());
        assertEquals(failed, run.get().stepStatuses().get(deployTester));
        assertEquals(unfinished, run.get().stepStatuses().get(installTester));
        assertEquals(succeeded, run.get().stepStatuses().get(report));
        // deployTester, plus all forced steps:
        assertStepsWithStartTime(run.get(), deployTester, copyVespaLogs, deactivateTester, deactivateReal, report);

        assertEquals(2, jobs.runs(id, systemTest).size());

        // Start a third run, then unregister and wait for data to be deleted.
        start(jobs, id, systemTest);
        tester.applications().deleteInstance(id);
        runner.maintain();
        assertFalse(jobs.last(id, systemTest).isPresent());
        assertTrue(jobs.runs(id, systemTest).isEmpty());
    }

    private void assertStepsWithStartTime(Run lastRun, Step... stepsWithStartTime) {
        Set<Step> actualStepsWithStartTime = lastRun.steps().entrySet().stream()
                .filter(entry -> entry.getValue().startTime().isPresent())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        assertEquals(Set.of(stepsWithStartTime), actualStepsWithStartTime);
    }

    @Test
    void locksAndGarbage() throws InterruptedException, BrokenBarrierException {
        DeploymentTester tester = new DeploymentTester();
        JobController jobs = tester.controller().jobController();
        // Hang during tester deployment, until notified.
        CyclicBarrier barrier = new CyclicBarrier(2);
        JobRunner runner = new JobRunner(tester.controller(), Duration.ofDays(1), Executors.newFixedThreadPool(32), waitingRunner(barrier));

        TenantAndApplicationId appId = tester.createApplication("tenant", "real", "default").id();
        ApplicationId id = appId.defaultInstance();
        byte[] testPackageBytes = new byte[0];
        jobs.submit(appId, submission(applicationPackage, testPackageBytes), 2);

        RunId runId = new RunId(id, systemTest, 1);
        start(jobs, id, systemTest);
        runner.maintain();
        barrier.await();
        try {
            jobs.locked(id, systemTest, deactivateTester, step -> {
            });
            fail("deployTester step should still be locked!");
        }
        catch (TimeoutException ignored) {
        }

        // Thread is still trying to deploy tester -- delete application, and see all data is garbage collected.
        assertEquals(Collections.singletonList(runId), jobs.active().stream().map(run -> run.id()).toList());
        tester.controllerTester().controller().applications().deleteApplication(TenantAndApplicationId.from(id), tester.controllerTester().credentialsFor(id.tenant()));
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

    @Test
    void historyPruning() {
        DeploymentTester tester = new DeploymentTester();
        JobController jobs = tester.controller().jobController();
        JobRunner runner = new JobRunner(tester.controller(), Duration.ofDays(1), inThreadExecutor(), (id, step) -> Optional.of(running));

        TenantAndApplicationId appId = tester.createApplication("tenant", "real", "default").id();
        ApplicationId instanceId = appId.defaultInstance();
        JobId jobId = new JobId(instanceId, systemTest);
        byte[] testPackageBytes = new byte[0];
        jobs.submit(appId, submission(applicationPackage, testPackageBytes), 2);
        assertFalse(jobs.lastSuccess(jobId).isPresent());

        for (int i = 0; i < jobs.historyLength(); i++) {
            start(jobs, instanceId, systemTest);
            runner.run();
        }

        assertEquals(64, jobs.runs(jobId).size());
        assertTrue(jobs.details(new RunId(instanceId, systemTest, 1)).isPresent());

        start(jobs, instanceId, systemTest);
        runner.run();

        assertEquals(64, jobs.runs(jobId).size());
        assertEquals(2, jobs.runs(jobId).keySet().iterator().next().number());
        assertFalse(jobs.details(new RunId(instanceId, systemTest, 1)).isPresent());
        assertTrue(jobs.details(new RunId(instanceId, systemTest, 65)).isPresent());

        JobRunner failureRunner = new JobRunner(tester.controller(), Duration.ofDays(1), inThreadExecutor(), (id, step) -> Optional.of(error));

        // Make all but the oldest of the 54 jobs a failure.
        for (int i = 0; i < jobs.historyLength() - 1; i++) {
            start(jobs, instanceId, systemTest);
            failureRunner.run();
        }
        assertEquals(64, jobs.runs(jobId).size());
        assertEquals(65, jobs.runs(jobId).keySet().iterator().next().number());
        assertEquals(65, jobs.lastSuccess(jobId).get().id().number());
        assertEquals(66, jobs.firstFailing(jobId).get().id().number());

        // Oldest success is kept even though it would normally overflow.
        start(jobs, instanceId, systemTest);
        failureRunner.run();
        assertEquals(65, jobs.runs(jobId).size());
        assertEquals(65, jobs.runs(jobId).keySet().iterator().next().number());
        assertEquals(65, jobs.lastSuccess(jobId).get().id().number());
        assertEquals(66, jobs.firstFailing(jobId).get().id().number());

        // First failure after the last success is also kept.
        start(jobs, instanceId, systemTest);
        failureRunner.run();
        assertEquals(66, jobs.runs(jobId).size());
        assertEquals(65, jobs.runs(jobId).keySet().iterator().next().number());
        assertEquals(66, jobs.runs(jobId).keySet().stream().skip(1).iterator().next().number());
        assertEquals(65, jobs.lastSuccess(jobId).get().id().number());
        assertEquals(66, jobs.firstFailing(jobId).get().id().number());

        // No other jobs are kept with repeated failures.
        start(jobs, instanceId, systemTest);
        failureRunner.run();
        assertEquals(66, jobs.runs(jobId).size());
        assertEquals(65, jobs.runs(jobId).keySet().iterator().next().number());
        assertEquals(66, jobs.runs(jobId).keySet().stream().skip(1).iterator().next().number());
        assertEquals(68, jobs.runs(jobId).keySet().stream().skip(2).iterator().next().number());
        assertEquals(65, jobs.lastSuccess(jobId).get().id().number());
        assertEquals(66, jobs.firstFailing(jobId).get().id().number());

        // history length returns to 256 when a new success is recorded.
        start(jobs, instanceId, systemTest);
        runner.run();
        assertEquals(64, jobs.runs(jobId).size());
        assertEquals(69, jobs.runs(jobId).keySet().iterator().next().number());
        assertEquals(132, jobs.lastSuccess(jobId).get().id().number());
        assertFalse(jobs.firstFailing(jobId).isPresent());
    }

    @Test
    void onlySuccessfulRunExpiresThenAnotherFails() {
        DeploymentTester tester = new DeploymentTester();
        JobController jobs = tester.controller().jobController();
        var app = tester.newDeploymentContext().submit();
        JobId jobId = new JobId(app.instanceId(), systemTest);
        assertFalse(jobs.lastSuccess(jobId).isPresent());

        app.runJob(systemTest);
        assertTrue(jobs.lastSuccess(jobId).isPresent());
        assertEquals(1, jobs.runs(jobId).size());

        tester.clock().advance(JobController.maxHistoryAge.plusSeconds(1));
        app.submit();
        app.failDeployment(systemTest);
        assertFalse(jobs.lastSuccess(jobId).isPresent());
        assertEquals(1, jobs.runs(jobId).size());
    }

    @Test
    void timeout() {
        DeploymentTester tester = new DeploymentTester();
        JobController jobs = tester.controller().jobController();
        Map<Step, RunStatus> outcomes = new EnumMap<>(Step.class);
        JobRunner runner = new JobRunner(tester.controller(), Duration.ofDays(1), inThreadExecutor(), mappedRunner(outcomes));

        TenantAndApplicationId appId = tester.createApplication("tenant", "real", "default").id();
        ApplicationId id = appId.defaultInstance();
        byte[] testPackageBytes = new byte[0];
        jobs.submit(appId, submission(applicationPackage, testPackageBytes), 2);

        start(jobs, id, systemTest);
        tester.clock().advance(JobRunner.jobTimeout.plus(Duration.ofSeconds(1)));
        runner.run();
        assertSame(aborted, jobs.last(id, systemTest).get().status());
    }

    @Test
    void jobMetrics() throws TimeoutException {
        DeploymentTester tester = new DeploymentTester();
        JobController jobs = tester.controller().jobController();
        Map<Step, RunStatus> outcomes = new EnumMap<>(Step.class);
        JobRunner runner = new JobRunner(tester.controller(), Duration.ofDays(1), inThreadExecutor(), mappedRunner(outcomes));

        TenantAndApplicationId appId = tester.createApplication("tenant", "real", "default").id();
        ApplicationId id = appId.defaultInstance();
        byte[] testPackageBytes = new byte[0];
        jobs.submit(appId, submission(applicationPackage, testPackageBytes), 2);

        for (Step step : JobProfile.of(systemTest).steps())
            outcomes.put(step, running);

        for (RunStatus status : RunStatus.values()) {
            if (status == success || status == reset) continue; // Status not used for steps.
            outcomes.put(deployTester, status);
            start(jobs, id, systemTest);
            runner.run();
            jobs.finish(jobs.last(id, systemTest).get().id());
        }

        Map<String, String> context = Map.of("applicationId", "tenant.real.default",
                "tenantName", "tenant",
                "app", "real.default",
                "test", "true",
                "zone", "test.us-east-1");
        MetricsMock metric = ((MetricsMock) tester.controller().metric());
        assertEquals(RunStatus.values().length - 2, metric.getMetric(context::equals, JobMetrics.start).get().intValue());
        assertEquals(1, metric.getMetric(context::equals, JobMetrics.abort).get().intValue());
        assertEquals(1, metric.getMetric(context::equals, JobMetrics.error).get().intValue());
        assertEquals(1, metric.getMetric(context::equals, JobMetrics.success).get().intValue());
        assertEquals(1, metric.getMetric(context::equals, JobMetrics.convergenceFailure).get().intValue());
        assertEquals(1, metric.getMetric(context::equals, JobMetrics.deploymentFailure).get().intValue());
        assertEquals(1, metric.getMetric(context::equals, JobMetrics.nodeAllocationFailure).get().intValue());
        assertEquals(1, metric.getMetric(context::equals, JobMetrics.endpointCertificateTimeout).get().intValue());
        assertEquals(1, metric.getMetric(context::equals, JobMetrics.testFailure).get().intValue());
        assertEquals(1, metric.getMetric(context::equals, JobMetrics.noTests).get().intValue());
    }

    private void start(JobController jobs, ApplicationId id, JobType type) {
        jobs.start(id, type, versions, false, Optional.empty());
    }

    public static ExecutorService inThreadExecutor() {
        return new AbstractExecutorService() {
            final AtomicBoolean shutDown = new AtomicBoolean(false);
            @Override public void shutdown() { shutDown.set(true); }
            @Override public List<Runnable> shutdownNow() { shutDown.set(true); return Collections.emptyList(); }
            @Override public boolean isShutdown() { return shutDown.get(); }
            @Override public boolean isTerminated() { return shutDown.get(); }
            @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
            @Override public void execute(Runnable command) { command.run(); }
        };
    }

    private static ExecutorService phasedExecutor(Phaser phaser) {
        return new AbstractExecutorService() {
            final ExecutorService delegate = Executors.newFixedThreadPool(32);
            @Override public void shutdown() { delegate.shutdown(); }
            @Override public List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
            @Override public boolean isShutdown() { return delegate.isShutdown(); }
            @Override public boolean isTerminated() { return delegate.isTerminated(); }
            @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException { return delegate.awaitTermination(timeout, unit); }
            @Override public void execute(Runnable command) {
                phaser.register();
                delegate.execute(() -> {
                    try { command.run(); }
                    finally { phaser.arriveAndDeregister(); }
                });
            }
        };
    }

    private static StepRunner mappedRunner(Map<Step, RunStatus> outcomes) {
        return (step, id) -> Optional.ofNullable(outcomes.get(step.get()));
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
            return Optional.of(running);
        };
    }

    private static Submission submission(ApplicationPackage applicationPackage, byte[] testPackage) {
        return new Submission(applicationPackage, testPackage, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Instant.EPOCH, 0);
    }

}
