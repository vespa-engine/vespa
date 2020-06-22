// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;

import ai.vespa.cloud.Zone;
import ai.vespa.hosted.cd.internal.TestRuntimeProvider;
import org.osgi.framework.BundleReference;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * The place to obtain environment-dependent configuration for test of a Vespa deployment.
 *
 * @author jvenstad
 * @author mortent
 */
public interface TestRuntime {
    static TestRuntime get() {
        var classloader = TestRuntime.class.getClassLoader();

        System.out.println("classloader.toString() = " + classloader.toString());
        System.out.println("classloader.getClass().toString() = " + classloader.getClass().toString());

        if (classloader instanceof BundleReference) {
            System.out.println("Loading Test runtime from osgi component");
            return Optional.ofNullable(TestRuntimeProvider.getTestRuntime())
                    .orElseThrow(() -> new RuntimeException("Component graph not ready, retrying"));
        } else {
            System.out.println("Loading Test runtime from service loader");
            ServiceLoader<TestRuntime> serviceLoader = ServiceLoader.load(TestRuntime.class, TestRuntime.class.getClassLoader());
            return serviceLoader.findFirst().orElseThrow(() -> new RuntimeException("No TestRuntime implementation found"));
        }
    }

    Deployment deploymentToTest();

    Zone zone();

}
