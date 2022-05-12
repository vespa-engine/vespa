package com.yahoo.vespa.testrunner;

import com.yahoo.vespa.testrunner.TestRunner.Suite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.EngineDiscoveryOrchestrator;
import org.junit.platform.launcher.core.EngineExecutionOrchestrator;
import org.junit.platform.launcher.core.LauncherDiscoveryResult;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.launcher.core.EngineDiscoveryOrchestrator.Phase.EXECUTION;

/**
 * @author jonmv
 */
class JunitRunnerTest {

    @Test
    void test() throws ExecutionException, InterruptedException {
        AtomicReference<byte[]> testRuntime = new AtomicReference<>();
        JunitRunner runner = new JunitRunner(testRuntime::set,
                                            __ -> List.of(HtmlLoggerTest.class),
                                            this::execute);

        runner.test(Suite.SYSTEM_TEST, new byte[0]).get();
        assertEquals(1, runner.getReport().successCount);
        assertEquals(0, runner.getReport().failedCount);
    }


    // For some inane reason, the JUnit test framework makes it impossible to simply launch a new instance of itself
    // from inside a unit test (run by itself) in the standard way, so all this kludge is necessary to work around that.
    void execute(LauncherDiscoveryRequest discoveryRequest, TestExecutionListener... listeners) {
        TestEngine testEngine = new JupiterTestEngine();
        LauncherDiscoveryResult discoveryResult = new EngineDiscoveryOrchestrator(Set.of(testEngine), Set.of()).discover(discoveryRequest, EXECUTION);
        TestDescriptor engineTestDescriptor = discoveryResult.getEngineTestDescriptor(testEngine);
        TestPlan plan = TestPlan.from(List.of(engineTestDescriptor), discoveryRequest.getConfigurationParameters());
        for (TestExecutionListener listener : listeners) listener.testPlanExecutionStarted(plan);
        new EngineExecutionOrchestrator().execute(discoveryResult, new ExecutionListenerAdapter(plan, listeners));
        for (TestExecutionListener listener : listeners) listener.testPlanExecutionFinished(plan);
    }

    static class ExecutionListenerAdapter implements EngineExecutionListener {

        private final TestPlan plan;
        private final List<TestExecutionListener> listeners;

        public ExecutionListenerAdapter(TestPlan plan, TestExecutionListener... listeners) {
            this.plan = plan;
            this.listeners = List.of(listeners);
        }

        private TestIdentifier getTestIdentifier(TestDescriptor testDescriptor) {
            return plan.getTestIdentifier(testDescriptor.getUniqueId().toString());
        }

        @Override public void dynamicTestRegistered(TestDescriptor testDescriptor) {
            TestIdentifier id = TestIdentifier.from(testDescriptor);
            plan.addInternal(id);
            for (TestExecutionListener listener : listeners)
                listener.dynamicTestRegistered(id);
        }

        @Override public void executionSkipped(TestDescriptor testDescriptor, String reason) {
            for (TestExecutionListener listener : listeners)
                listener.executionSkipped(getTestIdentifier(testDescriptor), reason);
        }

        @Override public void executionStarted(TestDescriptor testDescriptor) {
            for (TestExecutionListener listener : listeners)
                listener.executionStarted(getTestIdentifier(testDescriptor));
        }

        @Override public void executionFinished(TestDescriptor testDescriptor, TestExecutionResult testExecutionResult) {
            for (TestExecutionListener listener : listeners)
                listener.executionFinished(getTestIdentifier(testDescriptor), testExecutionResult);
        }

        @Override public void reportingEntryPublished(TestDescriptor testDescriptor, ReportEntry entry) {
            for (TestExecutionListener listener : listeners)
                listener.reportingEntryPublished(getTestIdentifier(testDescriptor), entry);
        }

    }

}
