// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import ai.vespa.cloud.Environment;
import ai.vespa.cloud.SystemInfo;
import ai.vespa.cloud.Zone;
import ai.vespa.hosted.cd.InconclusiveTestException;
import ai.vespa.hosted.cd.internal.TestRuntimeProvider;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.vespa.defaults.Defaults;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;

/**
 * @author mortent
 */
public class JunitRunner extends AbstractComponent implements TestRunner {

    private static final Logger logger = Logger.getLogger(JunitRunner.class.getName());

    private final SortedMap<Long, LogRecord> logRecords = new ConcurrentSkipListMap<>();
    private final TestRuntimeProvider testRuntimeProvider;
    private final Function<Suite, List<Class<?>>> classLoader;
    private final BiConsumer<LauncherDiscoveryRequest, TestExecutionListener[]> testExecutor;
    private volatile CompletableFuture<TestReport> execution;

    @Inject
    public JunitRunner(OsgiFramework osgiFramework,
                       JunitTestRunnerConfig config,
                       TestRuntimeProvider testRuntimeProvider,
                       SystemInfo systemInfo) {
        this(testRuntimeProvider,
             new TestBundleLoader(osgiFramework)::loadTestClasses,
             (discoveryRequest, listeners) -> LauncherFactory.create(LauncherConfig.builder()
                                                                                   .addTestEngines(new JupiterTestEngine())
                                                                                   .build()).execute(discoveryRequest, listeners));

        uglyHackSetCredentialsRootSystemProperty(config, systemInfo.zone());
    }

    JunitRunner(TestRuntimeProvider testRuntimeProvider,
                       Function<Suite, List<Class<?>>> classLoader,
                       BiConsumer<LauncherDiscoveryRequest, TestExecutionListener[]> testExecutor) {
        this.classLoader = classLoader;
        this.testExecutor = testExecutor;
        this.testRuntimeProvider = testRuntimeProvider;
    }

    @Override
    public CompletableFuture<?> test(Suite suite, byte[] testConfig) {
        if (execution != null && ! execution.isDone()) {
            throw new IllegalStateException("Test execution already in progress");
        }
        try {
            logRecords.clear();
            testRuntimeProvider.initialize(testConfig);
            execution = CompletableFuture.supplyAsync(() -> launchJunit(suite));
        } catch (Exception e) {
            execution = CompletableFuture.completedFuture(createReportWithFailedInitialization(e));
        }
        return execution;
    }

    @Override
    public Collection<LogRecord> getLog(long after) {
        return logRecords.tailMap(after + 1).values();
    }

    static TestReport createReportWithFailedInitialization(Exception exception) {
        TestReport.Failure failure = new TestReport.Failure("init", exception);
        return new TestReport.Builder().withFailures(List.of(failure))
                                       .withFailedCount(1)
                                       .build();
    }


    private TestReport launchJunit(Suite suite) {
        List<Class<?>> testClasses = classLoader.apply(suite);
        if (testClasses == null)
            return  null;

        VespaJunitLogListener logListener = new VespaJunitLogListener(record -> logRecords.put(record.getSequenceNumber(), record));
        SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
        LauncherDiscoveryRequest discoveryRequest = LauncherDiscoveryRequestBuilder.request()
                                                                                   .selectors(testClasses.stream()
                                                                                                         .map(DiscoverySelectors::selectClass)
                                                                                                         .collect(toList()))
                                                                                   .build();

        testExecutor.accept(discoveryRequest, new TestExecutionListener[] { logListener, summaryListener });

        var report = summaryListener.getSummary();
        var failures = report.getFailures().stream()
                             .map(failure -> {
                                 TestReport.trimStackTraces(failure.getException(), JunitRunner.class.getName());
                                 return new TestReport.Failure(VespaJunitLogListener.toString(failure.getTestIdentifier().getUniqueIdObject()),
                                                               failure.getException());
                             })
                             .collect(toList());

        // TODO: move to aggregator.
        long inconclusive = suite == Suite.PRODUCTION_TEST ? failures.stream()
                                                                     .filter(failure -> failure.exception() instanceof InconclusiveTestException)
                                                                     .count()
                                                           : 0;
        return TestReport.builder()
                         .withSuccessCount(report.getTestsSucceededCount())
                         .withAbortedCount(report.getTestsAbortedCount())
                         .withIgnoredCount(report.getTestsSkippedCount())
                         .withFailedCount(report.getTestsFailedCount() - inconclusive)
                         .withInconclusiveCount(inconclusive)
                         .withFailures(failures)
                         .withLogs(logRecords.values())
                         .build();
    }

    @Override
    public void deconstruct() {
        super.deconstruct();
    }

    @Override
    public TestRunner.Status getStatus() {
        if (execution == null) return TestRunner.Status.NOT_STARTED;
        if ( ! execution.isDone()) return TestRunner.Status.RUNNING;
        try {
            return execution.get() == null ? Status.NO_TESTS : execution.get().status();
        } catch (InterruptedException | ExecutionException e) {
            logger.log(Level.WARNING, "Error while getting test report", e);
            return TestRunner.Status.ERROR;
        }
    }

    @Override
    public TestReport getReport() {
        if (execution.isDone()) {
            try {
                return execution.get();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error getting test report", e);
                // Likely this is something wrong with the provided test bundle. Create a test report
                // and present in the console to enable tenants to act on it.
                return createReportWithFailedInitialization(e);
            }
        } else {
            return null;
        }
    }

    // TODO(bjorncs|tokle) Propagate credentials root without system property. Ideally move knowledge about path to test-runtime implementations
    private static void uglyHackSetCredentialsRootSystemProperty(JunitTestRunnerConfig config, Zone zone) {
        Optional<String> credentialsRoot;
        if (config.useAthenzCredentials()) {
            credentialsRoot = Optional.of(Defaults.getDefaults().underVespaHome("var/vespa/sia"));
        } else if (zone.environment() != Environment.prod){
            // Only set credentials in non-prod zones where not available
            credentialsRoot = Optional.of(config.artifactsPath().toString());
        } else {
            credentialsRoot = Optional.empty();
        }
        credentialsRoot.ifPresent(root -> System.setProperty("vespa.test.credentials.root", root));
    }

}
