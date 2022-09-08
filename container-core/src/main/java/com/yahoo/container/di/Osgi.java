// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.FileReference;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.bundle.MockBundle;
import org.osgi.framework.Bundle;

import java.util.Collection;
import java.util.Set;

import static java.util.Collections.emptySet;

/**
 * This interface has default implementations of all methods, to allow using it
 * for testing, instead of mocking or a test implementation.
 *
 * TODO: remove test code from this interface.
 *
 * @author gjoranv
 * @author Tony Vaagenes
 * @author ollivir
 */
public interface Osgi {

    enum GenerationStatus { SUCCESS, FAILURE }

    default void installPlatformBundles(Collection<String> bundlePaths) {
    }

    /**
     * Installs the new set of application bundles.
     *
     * @param bundles The bundles for the new application.
     * @param generation The generation number of the new application.
     */
    default void useApplicationBundles(Collection<FileReference> bundles, long generation) {
    }

    /**
     * If the current generation is a success, the set of bundles that is not needed by the new application
     * generation, and therefore should be scheduled for uninstalling, is returned.
     * If the current generation is a failure, all state related to application bundles is reverted to
     * the previous generation. The set of bundles that was exclusively needed by the new generation,
     * and therefore should be scheduled for uninstalling, is returned.
     *
     * @param status The success or failure of the new generation
     * @return The set of bundles that are no longer needed by the new or latest good generation.
     */
    default Set<Bundle> completeBundleGeneration(GenerationStatus status) {
        return emptySet();
    }

    default Class<?> resolveClass(BundleInstantiationSpecification spec) {
        try {
            return Class.forName(spec.classId.getName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    default Bundle getBundle(ComponentSpecification spec) {
        return new MockBundle();
    }
}
