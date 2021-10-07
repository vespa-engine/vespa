// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.utils.internal;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.test.ArraytypesConfig;
import com.yahoo.config.ChangesRequiringRestart;
import com.yahoo.config.ConfigInstance;
import com.yahoo.test.SimpletypesConfig;
import com.yahoo.vespa.config.ConfigKey;
import org.junit.Test;

import java.util.Set;

import static com.yahoo.vespa.model.utils.internal.ReflectionUtil.getAllConfigsProduced;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @author bjorncs
 * @author gjoranv
 * @since 5.1
 */
public class ReflectionUtilTest {

    private static class SimpleProducer extends AbstractConfigProducer implements SimpletypesConfig.Producer {
        SimpleProducer(AbstractConfigProducer parent, String subId) { super(parent, subId); }

        @Override
        public void getConfig(SimpletypesConfig.Builder builder) { }
    }

    private interface ProducerInterface extends SimpletypesConfig.Producer, ArraytypesConfig.Producer { }

    private static class InterfaceImplementingProducer extends AbstractConfigProducer implements ProducerInterface {
        InterfaceImplementingProducer(AbstractConfigProducer parent, String subId) { super(parent, subId); }

        @Override
        public void getConfig(ArraytypesConfig.Builder builder) { }
        @Override
        public void getConfig(SimpletypesConfig.Builder builder) { }
    }

    private static abstract class MyAbstractProducer extends AbstractConfigProducer implements SimpletypesConfig.Producer {
        MyAbstractProducer(AbstractConfigProducer parent, String subId) { super(parent, subId); }
        @Override
        public void getConfig(SimpletypesConfig.Builder builder) { }
    }

    private static class ConcreteProducer extends MyAbstractProducer {
        ConcreteProducer(AbstractConfigProducer parent, String subId) { super(parent, subId); }
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
    public void getAllConfigsProduced_includes_configs_produced_by_super_class() {
        Set<ConfigKey<?>> configs = getAllConfigsProduced(ConcreteProducer.class, "foo");
        assertThat(configs.size(), is(1));
        assertTrue(configs.contains(new ConfigKey<>(SimpletypesConfig.CONFIG_DEF_NAME, "foo", SimpletypesConfig.CONFIG_DEF_NAMESPACE)));
    }

    @Test
    public void getAllConfigsProduced_includes_configs_produced_by_implemented_interface() {
        Set<ConfigKey<?>> configs = getAllConfigsProduced(InterfaceImplementingProducer.class, "foo");
        assertThat(configs.size(), is(2));
        assertTrue(configs.contains(new ConfigKey<>(SimpletypesConfig.CONFIG_DEF_NAME, "foo", SimpletypesConfig.CONFIG_DEF_NAMESPACE)));
        assertTrue(configs.contains(new ConfigKey<>(ArraytypesConfig.CONFIG_DEF_NAME, "foo", ArraytypesConfig.CONFIG_DEF_NAMESPACE)));
    }

    @Test
    public void getAllConfigsProduced_includes_configs_directly_implemented_by_producer() {
        Set<ConfigKey<?>> configs = getAllConfigsProduced(SimpleProducer.class, "foo");
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
