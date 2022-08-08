// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import ai.vespa.cloud.Environment;
import ai.vespa.cloud.SystemInfo;
import ai.vespa.cloud.Zone;
import ai.vespa.hosted.cd.internal.TestRuntimeProvider;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.vespa.defaults.Defaults;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.time.Clock;
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
 * @author jonmv
 */
public class JunitRunner extends AbstractComponent implements TestRunner {

    private static final Logger logger = Logger.getLogger(JunitRunner.class.getName());

    private final Clock clock;
    private final SortedMap<Long, LogRecord> logRecords = new ConcurrentSkipListMap<>();
    private final TeeStream stdoutTee = TeeStream.ofSystemOut();
    private final TeeStream stderrTee = TeeStream.ofSystemErr();
    private final TestRuntimeProvider testRuntimeProvider;
    private final Function<Suite, List<Class<?>>> classLoader;
    private final BiConsumer<LauncherDiscoveryRequest, TestExecutionListener[]> testExecutor;
    private volatile CompletableFuture<TestReport> execution;

    @Inject
    public JunitRunner(OsgiFramework osgiFramework,
                       JunitTestRunnerConfig config,
                       TestRuntimeProvider testRuntimeProvider,
                       SystemInfo systemInfo) {
        this(Clock.systemUTC(),
             testRuntimeProvider,
             new TestBundleLoader(osgiFramework)::loadTestClasses,
             JunitRunner::executeTests);

        uglyHackSetCredentialsRootSystemProperty(config, systemInfo.zone());

    }

    JunitRunner(Clock clock,
                TestRuntimeProvider testRuntimeProvider,
                Function<Suite, List<Class<?>>> classLoader,
                BiConsumer<LauncherDiscoveryRequest, TestExecutionListener[]> testExecutor) {
        this.clock = clock;
        this.classLoader = classLoader;
        this.testExecutor = testExecutor;
        this.testRuntimeProvider = testRuntimeProvider;
    }

    private static void executeTests(LauncherDiscoveryRequest discoveryRequest, TestExecutionListener[] listeners) {
        var launcher = LauncherFactory.create(LauncherConfig.builder()
                                                            .addTestEngines(new JupiterTestEngine())
                                                            .build());
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        try {
            // Pick the bundle class loader of the first user test class, from the test class selector.
            discoveryRequest.getSelectorsByType(ClassSelector.class).stream()
                            .map(selector -> selector.getJavaClass().getClassLoader())
                            .findAny().ifPresent(Thread.currentThread()::setContextClassLoader);

            launcher.execute(discoveryRequest, listeners);
        }
        finally {
            Thread.currentThread().setContextClassLoader(context);
        }
    }

    @Override
    public CompletableFuture<?> test(Suite suite, byte[] testConfig) {
        if (execution != null && ! execution.isDone()) {
            throw new IllegalStateException("Test execution already in progress");
        }
        try {
            logRecords.clear();
            execution = CompletableFuture.supplyAsync(() -> launchJunit(suite, testConfig));
        } catch (Throwable t) {
            execution = CompletableFuture.completedFuture(TestReport.createFailed(clock, suite, t));
        }
        return execution;
    }

    @Override
    public Collection<LogRecord> getLog(long after) {
        return logRecords.tailMap(after + 1).values();
    }

    private TestReport launchJunit(Suite suite, byte[] testConfig) {
        List<Class<?>> testClasses = classLoader.apply(suite);
        if (testClasses == null)
            return  null;

        testRuntimeProvider.initialize(testConfig);
        TestReportGeneratingListener testReportListener = new TestReportGeneratingListener(suite,
                                                                                           record -> logRecords.put(record.getSequenceNumber(), record),
                                                                                           stdoutTee,
                                                                                           stderrTee,
                                                                                           clock);
        LauncherDiscoveryRequest discoveryRequest = LauncherDiscoveryRequestBuilder.request()
                                                                                   .selectors(testClasses.stream()
                                                                                                         .map(DiscoverySelectors::selectClass)
                                                                                                         .collect(toList()))
                                                                                   .build();
        testExecutor.accept(discoveryRequest, new TestExecutionListener[] { testReportListener });

        return testReportListener.report();
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
            return testRunnerStatus(execution.get());
        } catch (InterruptedException | ExecutionException e) {
            logger.log(Level.WARNING, "Error while getting test report", e);
            return TestRunner.Status.ERROR;
        }
    }

    static TestRunner.Status testRunnerStatus(TestReport report) {
        if (report == null) return Status.NO_TESTS;
        switch (report.root().status()) {
            case error:
            case failed:       return Status.FAILURE;
            case inconclusive: return Status.INCONCLUSIVE;
            case successful:
            case skipped:
            case aborted:     return report.root().tally().containsKey(TestReport.Status.successful) ? Status.SUCCESS
                                                                                                     : Status.NO_TESTS;
            default: throw new IllegalStateException("unknown status '" + report.root().status() + "'");
        }
    }

    @Override
    public TestReport getReport() {
        if (execution.isDone()) {
            try {
                return execution.get();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error getting test report", t);
                // Likely this is something wrong with the provided test bundle. Create a test report
                // and present in the console to enable tenants to act on it.
                return TestReport.createFailed(clock, null, t);
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
