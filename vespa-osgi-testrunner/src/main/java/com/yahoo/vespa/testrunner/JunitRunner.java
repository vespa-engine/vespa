// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import ai.vespa.hosted.api.TestDescriptor;
import ai.vespa.hosted.cd.internal.TestRuntimeProvider;
import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.exception.ExceptionUtils;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.LoggingListener;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author mortent
 */
public class JunitRunner extends AbstractComponent {
    private static final Logger logger = Logger.getLogger(JunitRunner.class.getName());

    private static final String TEST_BUNDLE_SUFFIX = "-tests";

    private final BundleContext bundleContext;
    private final TestRuntimeProvider testRuntimeProvider;

    @Inject
    public JunitRunner(OsgiFramework osgiFramework, TestRuntimeProvider testRuntimeProvider) {
        this.testRuntimeProvider = testRuntimeProvider;
        var tmp = osgiFramework.bundleContext();
        try {
            var field = tmp.getClass().getDeclaredField("wrapped");
            field.setAccessible(true);
            bundleContext = (BundleContext) field.get(tmp);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public String executeTests(TestDescriptor.TestCategory category, byte[] testConfig) {
        testRuntimeProvider.initialize(testConfig);
        Bundle testBundle = findTestBundle();
        Optional<TestDescriptor> testDescriptor = loadTestDescriptor(testBundle);
        if (testDescriptor.isEmpty()) {
            throw new RuntimeException("Could not find test descriptor");
        }
        List<Class<?>> testClasses = loadClasses(testBundle, testDescriptor.get(), category);
        return launchJunit(testClasses);
    }

    public boolean isSupported() {
        Bundle testBundle = findTestBundle();
        return loadTestDescriptor(testBundle).isPresent();
    }

    private Bundle findTestBundle() {
        return Stream.of(bundleContext.getBundles())
                .filter(bundle -> bundle.getSymbolicName().endsWith(TEST_BUNDLE_SUFFIX))
                .findAny()
                .orElseThrow(() -> new RuntimeException("No bundle on classpath with name ending on " + TEST_BUNDLE_SUFFIX));
    }

    private Optional<TestDescriptor> loadTestDescriptor(Bundle bundle) {
        URL resource = bundle.getEntry(TestDescriptor.DEFAULT_FILENAME);
        TestDescriptor testDescriptor;
        try {
            var jsonDescriptor = IOUtils.readAll(resource.openStream(), Charset.defaultCharset()).trim();
            testDescriptor = TestDescriptor.fromJsonString(jsonDescriptor);
            logger.info( "Test classes in bundle :" + testDescriptor.toString());
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
        logger.info("Loaded testClasses: \n" + buffer.toString());
        return testClasses;
    }

    private Class<?> loadClass(Bundle bundle, String className) {
        try {
            return bundle.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find class: " + className + " in bundle " + bundle.getSymbolicName(), e);
        }
    }

    private String launchJunit(List<Class<?>> testClasses) {
        LauncherDiscoveryRequest discoveryRequest = LauncherDiscoveryRequestBuilder.request()
                .selectors(
                        testClasses.stream().map(DiscoverySelectors::selectClass).collect(Collectors.toList())
                )
                .build();

        var launcherConfig = LauncherConfig.builder()
                .addTestEngines(new JupiterTestEngine())

                .build();
        Launcher launcher = LauncherFactory.create(launcherConfig);

        // Create log listener:
        var logLines = new ArrayList<String>();
        var logListener = LoggingListener.forBiConsumer((t, m) -> log(logLines, m.get(), t));
        // Create a summary listener:
        var summaryListener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(logListener, summaryListener);

        // Execute request
        launcher.execute(discoveryRequest);

        var report = summaryListener.getSummary();

        return createJsonTestReport(report, logLines);
    }

    private String createJsonTestReport(TestExecutionSummary report, List<String> logLines) {
        var slime = new Slime();
        var root = slime.setObject();
        var summary = root.setObject("summary");
        summary.setLong("Total tests", report.getTestsFoundCount());
        summary.setLong("Test success", report.getTestsSucceededCount());
        summary.setLong("Test failed", report.getTestsFailedCount());
        summary.setLong("Test ignored", report.getTestsSkippedCount());
        summary.setLong("Test success", report.getTestsAbortedCount());
        summary.setLong("Test started", report.getTestsStartedCount());
        var failures = summary.setArray("failures");
        report.getFailures().forEach(failure -> serializeFailure(failure, failures.addObject()));

        var output = root.setArray("output");
        logLines.forEach(output::addString);

        return Exceptions.uncheck(() -> new String(SlimeUtils.toJsonBytes(slime), StandardCharsets.UTF_8));
    }

    private void serializeFailure(TestExecutionSummary.Failure failure, Cursor slime) {
        var testIdentifier = failure.getTestIdentifier();
        slime.setString("testName", testIdentifier.getUniqueId());
        slime.setString("testError",failure.getException().getMessage());
        slime.setString("exception", ExceptionUtils.getStackTraceAsString(failure.getException()));
    }

    private void log(List<String> logs, String message, Throwable t) {
        logs.add(message);
        if(t != null) {
            logs.add(t.getMessage());
            List.of(t.getStackTrace()).stream()
                    .map(StackTraceElement::toString)
                    .forEach(logs::add);
        }
    }

    @Override
    public void deconstruct() {
        super.deconstruct();
    }
}
