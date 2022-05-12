package com.yahoo.vespa.testrunner;

import ai.vespa.hosted.api.TestDescriptor;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.vespa.testrunner.TestRunner.Suite;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

/**
 * @author mortent
 */
class TestBundleLoader {

    private static final Logger logger = Logger.getLogger(TestBundleLoader.class.getName());

    private final BundleContext bundleContext;

    public TestBundleLoader(OsgiFramework osgi) {
        this.bundleContext = getUnrestrictedBundleContext(osgi);
    }

    List<Class<?>> loadTestClasses(Suite suite) {
        Optional<Bundle> testBundle = findTestBundle();
        if (testBundle.isEmpty())
            return null;

        Optional<TestDescriptor> testDescriptor = loadTestDescriptor(testBundle.get());
        if (testDescriptor.isEmpty())
            throw new RuntimeException("Could not find test descriptor");

        return loadClasses(testBundle.get(), testDescriptor.get(), toCategory(suite));
    }

    private Optional<Bundle> findTestBundle() {
        return Stream.of(bundleContext.getBundles()).filter(this::isTestBundle).findAny();
    }

    private boolean isTestBundle(Bundle bundle) {
        String testBundleHeader = bundle.getHeaders().get("X-JDisc-Test-Bundle-Version");
        return testBundleHeader != null && ! testBundleHeader.isBlank();
    }

    private static Optional<TestDescriptor> loadTestDescriptor(Bundle bundle) {
        URL resource = bundle.getEntry(TestDescriptor.DEFAULT_FILENAME);
        TestDescriptor testDescriptor;
        try {
            var jsonDescriptor = IOUtils.readAll(resource.openStream(), UTF_8).trim();
            testDescriptor = TestDescriptor.fromJsonString(jsonDescriptor);
            logger.info("Test classes in bundle: " + testDescriptor);
            return Optional.of(testDescriptor);
        }
        catch (IOException e) {
            return Optional.empty();
        }
    }

    private List<Class<?>> loadClasses(Bundle bundle, TestDescriptor testDescriptor, TestDescriptor.TestCategory testCategory) {
        List<Class<?>> testClasses = testDescriptor.getConfiguredTests(testCategory).stream()
                                                   .map(className -> loadClass(bundle, className))
                                                   .collect(toList());

        StringBuffer buffer = new StringBuffer();
        testClasses.forEach(cl -> buffer.append("\t").append(cl.toString()).append(" / ").append(cl.getClassLoader().toString()).append("\n"));
        logger.info("Loaded testClasses: \n" + buffer);
        return testClasses;
    }

    private Class<?> loadClass(Bundle bundle, String className) {
        try {
            return bundle.loadClass(className);
        }
        catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find class: " + className + " in bundle " + bundle.getSymbolicName(), e);
        }
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

    // Hack to retrieve bundle context that allows access to other bundles
    private static BundleContext getUnrestrictedBundleContext(OsgiFramework framework) {
        try {
            BundleContext restrictedBundleContext = framework.bundleContext();
            var field = restrictedBundleContext.getClass().getDeclaredField("wrapped");
            field.setAccessible(true);
            return (BundleContext) field.get(restrictedBundleContext);
        }
        catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

}
