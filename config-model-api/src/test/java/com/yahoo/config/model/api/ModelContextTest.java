// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.component.Version;
import org.junit.Test;

import com.yahoo.text.Text;
import java.lang.reflect.Method;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Bjørn Christian Seime
 */
public class ModelContextTest {

    @Test
    public void verify_all_feature_flag_methods_have_annotation() {
        for (Method method : ModelContext.FeatureFlags.class.getDeclaredMethods()) {
            if (ignoreMethod(method)) continue;
            assertNotNull(
                    Text.format(
                            "Method '%s' is not annotated with '%s'",
                            method.getName(), ModelContext.ModelFeatureFlag.class.getSimpleName()),
                    method.getDeclaredAnnotation(ModelContext.ModelFeatureFlag.class));
        }
    }

    @Test
    public void verify_all_feature_flag_methods_have_default_implementation() {
        for (Method method : ModelContext.FeatureFlags.class.getDeclaredMethods()) {
            if (ignoreMethod(method)) continue;
            assertTrue(
                    Text.format("Method '%s' has no default implementation", method.getName()),
                    method.isDefault());
        }
    }

    @Test
    public void verify_unused_since_is_a_version_with_major_and_minor() {
        for (Method method : ModelContext.FeatureFlags.class.getDeclaredMethods()) {
            if (ignoreMethod(method)) continue;
            ModelContext.ModelFeatureFlag annotation = method.getDeclaredAnnotation(ModelContext.ModelFeatureFlag.class);
            if (annotation == null || annotation.unusedSince().isEmpty()) continue;
            assertTrue(Text.format("Method '%s' has unusedSince '%s' which must be on the form major.minor or major.minor.patch",
                                   method.getName(), annotation.unusedSince()),
                       annotation.unusedSince().matches("\\d+\\.\\d+(\\.\\d+)?"));
            Version.fromString(annotation.unusedSince()); // Must also be accepted by Version
        }
    }

    private static boolean ignoreMethod(Method method) {
        // If a default interface method "foo" has a lambda, e.g. `() -> false`, then OpenJDK 17 appears to create
        // a synthetic method in the interface called "lambda$foo$0".
        return method.isSynthetic();
    }

}
