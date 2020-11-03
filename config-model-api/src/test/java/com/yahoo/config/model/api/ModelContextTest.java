// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author bjorncs
 */
public class ModelContextTest {

    @Test
    public void verify_all_feature_flag_methods_have_annotation() {
        for (Method method : ModelContext.FeatureFlags.class.getDeclaredMethods()) {
            assertNotNull(
                    String.format(
                            "Method '%s' is not annotated with '%s'",
                            method.getName(), ModelContext.ModelFeatureFlag.class.getSimpleName()),
                    method.getDeclaredAnnotation(ModelContext.ModelFeatureFlag.class));
        }
    }

    @Test
    public void verify_all_feature_flag_methods_have_default_implementation() {
        for (Method method : ModelContext.FeatureFlags.class.getDeclaredMethods()) {
            assertTrue(
                    String.format("Method '%s' has no default implementation", method.getName()),
                    method.isDefault());
        }
    }

}