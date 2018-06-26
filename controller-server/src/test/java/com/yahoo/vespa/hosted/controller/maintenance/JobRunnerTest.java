package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.ControllerTester;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author jonmv
 */
public class JobRunnerTest {

    @Test
    public void test() {
        ControllerTester tester = new ControllerTester();
        JobRunner runner = new JobRunner(tester.controller(), Duration.ofDays(1), new JobControl(tester.curator()),
                                         inThreadExecutor(), new DummyStepRunner());
        runner.maintain();
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

}
