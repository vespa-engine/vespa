// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.utils.internal;

import com.yahoo.test.ArraytypesConfig;
import com.yahoo.config.ChangesRequiringRestart;
import com.yahoo.config.ConfigInstance;
import com.yahoo.test.SimpletypesConfig;
import com.yahoo.vespa.config.ConfigKey;
import org.junit.Test;

import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @author bjorncs
 * @since 5.1
 */
public class ReflectionUtilTest {

    private static interface ComplexInterface extends SimpletypesConfig.Producer, ArraytypesConfig.Producer {
    }

    private static class SimpleProducer implements SimpletypesConfig.Producer {
        @Override
        public void getConfig(SimpletypesConfig.Builder builder) {
        }
    }

    private static class ComplexProducer implements ComplexInterface {
        @Override
        public void getConfig(ArraytypesConfig.Builder builder) {
        }
        @Override
        public void getConfig(SimpletypesConfig.Builder builder) {
        }
    }

    private static class RestartConfig extends ConfigInstance {
        @SuppressWarnings("UnusedDeclaration")
        private static boolean containsFieldsFlaggedWithRestart() {
            return true;
        }

        @SuppressWarnings("UnusedDeclaration")
        private ChangesRequiringRestart getChangesRequiringRestart(RestartConfig newConfig) {
            return new ChangesRequiringRestart("testing");
        }
    }

    private static class NonRestartConfig extends ConfigInstance {}

    @Test
    public void requireThatConfigsProducedByInterfaceTakesParentIntoAccount() {
        Set<ConfigKey<?>> configs = ReflectionUtil.configsProducedByInterface(ComplexProducer.class, "foo");
        assertThat(configs.size(), is(2));
        assertTrue(configs.contains(new ConfigKey<>(SimpletypesConfig.CONFIG_DEF_NAME, "foo", SimpletypesConfig.CONFIG_DEF_NAMESPACE)));
        assertTrue(configs.contains(new ConfigKey<>(ArraytypesConfig.CONFIG_DEF_NAME, "foo", ArraytypesConfig.CONFIG_DEF_NAMESPACE)));
    }

    @Test
    public void requireThatConfigsProducedByInterfaceAreFound() {
        Set<ConfigKey<?>> configs = ReflectionUtil.configsProducedByInterface(SimpleProducer.class, "foo");
        assertThat(configs.size(), is(1));
        assertTrue(configs.contains(new ConfigKey<>(SimpletypesConfig.CONFIG_DEF_NAME, "foo", SimpletypesConfig.CONFIG_DEF_NAMESPACE)));
    }

    @Test
    public void requireThatRestartMethodsAreDetectedProperly() {
        assertFalse(ReflectionUtil.hasRestartMethods(NonRestartConfig.class));
        assertTrue(ReflectionUtil.hasRestartMethods(RestartConfig.class));
    }

    @Test
    public void requireThatRestartMethodsAreProperlyInvoked() {
        assertTrue(ReflectionUtil.containsFieldsFlaggedWithRestart(RestartConfig.class));
        assertEquals("testing", ReflectionUtil.getChangesRequiringRestart(new RestartConfig(), new RestartConfig()).getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void requireThatGetChangesRequiringRestartValidatesParameterTypes() {
        ReflectionUtil.getChangesRequiringRestart(new RestartConfig(), new NonRestartConfig());
    }


}
