// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import ai.vespa.cloud.Environment;
import ai.vespa.cloud.SystemInfo;
import ai.vespa.cloud.Zone;
import ai.vespa.hosted.api.TestDescriptor;
import ai.vespa.hosted.cd.InconclusiveTestException;
import ai.vespa.hosted.cd.internal.TestRuntimeProvider;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.vespa.defaults.Defaults;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author mortent
 */
public class JunitRunner extends AbstractComponent implements TestRunner {

    private static final Logger logger = Logger.getLogger(JunitRunner.class.getName());

    private final SortedMap<Long, LogRecord> logRecords = new ConcurrentSkipListMap<>();
    private final BundleContext bundleContext;
    private final TestRuntimeProvider testRuntimeProvider;
    private volatile CompletableFuture<TestReport> execution;

    @Inject
    public JunitRunner(OsgiFramework osgiFramework,
                       JunitTestRunnerConfig config,
                       TestRuntimeProvider testRuntimeProvider,
                       SystemInfo systemInfo) {
        this.testRuntimeProvider = testRuntimeProvider;
        this.bundleContext = getUnrestrictedBundleContext(osgiFramework);
        uglyHackSetCredentialsRootSystemProperty(config, systemInfo.zone());
    }

    // Hack to retrieve bundle context that allows access to other bundles
    private static BundleContext getUnrestrictedBundleContext(OsgiFramework framework) {
        try {
            BundleContext restrictedBundleContext = framework.bundleContext();
            var field = restrictedBundleContext.getClass().getDeclaredField("wrapped");
            field.setAccessible(true);
            return  (BundleContext) field.get(restrictedBundleContext);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
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

    private static TestDescriptor.TestCategory toCategory(TestRunner.Suite testProfile) {
        switch(testProfile) {
            case SYSTEM_TEST: return TestDescriptor.TestCategory.systemtest;
            case STAGING_SETUP_TEST: return TestDescriptor.TestCategory.stagingsetuptest;
            case STAGING_TEST: return TestDescriptor.TestCategory.stagingtest;
            case PRODUCTION_TEST: return TestDescriptor.TestCategory.productiontest;
            default: throw new RuntimeException("Unknown test profile: " + testProfile.name());
        }
    }

    @Override
    public CompletableFuture<?> test(Suite suite, byte[] testConfig) {
        if (execution != null && ! execution.isDone()) {
            throw new IllegalStateException("Test execution already in progress");
        }
        try {
            logRecords.clear();
            Optional<Bundle> testBundle = findTestBundle();
            if (testBundle.isEmpty()) {
                execution = CompletableFuture.completedFuture(null);
                return execution;
            }

            testRuntimeProvider.initialize(testConfig);
            Optional<TestDescriptor> testDescriptor = loadTestDescriptor(testBundle.get());
            if (testDescriptor.isEmpty()) {
                throw new RuntimeException("Could not find test descriptor");
            }
            execution = CompletableFuture.supplyAsync(() -> launchJunit(loadClasses(testBundle.get(), testDescriptor.get(), toCategory(suite)),
                                                                        suite == Suite.PRODUCTION_TEST));
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

    private Optional<Bundle> findTestBundle() {
        return Stream.of(bundleContext.getBundles())
                     .filter(this::isTestBundle)
                     .findAny();
    }

    private boolean isTestBundle(Bundle bundle) {
        var testBundleHeader = bundle.getHeaders().get("X-JDisc-Test-Bundle-Version");
        return testBundleHeader != null && ! testBundleHeader.isBlank();
    }

    private Optional<TestDescriptor> loadTestDescriptor(Bundle bundle) {
        URL resource = bundle.getEntry(TestDescriptor.DEFAULT_FILENAME);
        TestDescriptor testDescriptor;
        try {
            var jsonDescriptor = IOUtils.readAll(resource.openStream(), Charset.defaultCharset()).trim();
            testDescriptor = TestDescriptor.fromJsonString(jsonDescriptor);
            logger.info("Test classes in bundle: " + testDescriptor);
            return Optional.of(testDescriptor);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private List<Class<?>> loadClasses(Bundle bundle, TestDescriptor testDescriptor, TestDescriptor.TestCategory testCategory) {
        List<Class<?>> testClasses = testDescriptor.getConfiguredTests(testCategory).stream()
                .map(className -> loadClass(bundle, className))
                .collect(Collectors.toList());

        StringBuffer buffer = new StringBuffer();
        testClasses.forEach(cl -> buffer.append("\t").append(cl.toString()).append(" / ").append(cl.getClassLoader().toString()).append("\n"));
        logger.info("Loaded testClasses: \n" + buffer);
        return testClasses;
    }

    private Class<?> loadClass(Bundle bundle, String className) {
        try {
            return bundle.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find class: " + className + " in bundle " + bundle.getSymbolicName(), e);
        }
    }

    private TestReport launchJunit(List<Class<?>> testClasses, boolean isProductionTest) {
        LauncherDiscoveryRequest discoveryRequest = LauncherDiscoveryRequestBuilder.request()
                .selectors(testClasses.stream().map(DiscoverySelectors::selectClass).collect(Collectors.toList()))
                .build();

        var launcherConfig = LauncherConfig.builder()
                .addTestEngines(new JupiterTestEngine())

                .build();
        Launcher launcher = LauncherFactory.create(launcherConfig);

        // Create log listener:
        var logListener = new VespaJunitLogListener(record -> logRecords.put(record.getSequenceNumber(), record));
        // Create a summary listener:
        var summaryListener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(logListener, summaryListener);

        // Execute request
        launcher.execute(discoveryRequest);
        var report = summaryListener.getSummary();
        var failures = report.getFailures().stream()
                .map(failure -> {
                    TestReport.trimStackTraces(failure.getException(), JunitRunner.class.getName());
                    return new TestReport.Failure(VespaJunitLogListener.toString(failure.getTestIdentifier().getUniqueIdObject()),
                                                  failure.getException());
                })
                .collect(Collectors.toList());
        long inconclusive = isProductionTest ? failures.stream()
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

}
