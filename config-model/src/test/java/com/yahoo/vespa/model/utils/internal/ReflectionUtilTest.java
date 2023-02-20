// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.utils.internal;

import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.test.ArraytypesConfig;
import com.yahoo.config.ChangesRequiringRestart;
import com.yahoo.config.ConfigInstance;
import com.yahoo.test.SimpletypesConfig;
import com.yahoo.vespa.config.ConfigKey;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.yahoo.vespa.model.utils.internal.ReflectionUtil.getAllConfigsProduced;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Ulf Lilleengen
 * @author bjorncs
 * @author gjoranv
 * @since 5.1
 */
public class ReflectionUtilTest {

    private static class SimpleProducer extends TreeConfigProducer implements SimpletypesConfig.Producer {
        SimpleProducer(TreeConfigProducer parent, String subId) { super(parent, subId); }

        @Override
        public void getConfig(SimpletypesConfig.Builder builder) { }
    }

    private interface ProducerInterface extends SimpletypesConfig.Producer, ArraytypesConfig.Producer { }

    private static class InterfaceImplementingProducer extends TreeConfigProducer implements ProducerInterface {
        InterfaceImplementingProducer(TreeConfigProducer parent, String subId) { super(parent, subId); }

        @Override
        public void getConfig(ArraytypesConfig.Builder builder) { }
        @Override
        public void getConfig(SimpletypesConfig.Builder builder) { }
    }

    private static abstract class MyAbstractProducer extends TreeConfigProducer implements SimpletypesConfig.Producer {
        MyAbstractProducer(TreeConfigProducer parent, String subId) { super(parent, subId); }
        @Override
        public void getConfig(SimpletypesConfig.Builder builder) { }
    }

    private static class ConcreteProducer extends MyAbstractProducer {
        ConcreteProducer(TreeConfigProducer parent, String subId) { super(parent, subId); }
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
    void getAllConfigsProduced_includes_configs_produced_by_super_class() {
        Set<ConfigKey<?>> configs = getAllConfigsProduced(ConcreteProducer.class, "foo");
        assertEquals(1, configs.size());
        assertTrue(configs.contains(new ConfigKey<>(SimpletypesConfig.CONFIG_DEF_NAME, "foo", SimpletypesConfig.CONFIG_DEF_NAMESPACE)));
    }

    @Test
    void getAllConfigsProduced_includes_configs_produced_by_implemented_interface() {
        Set<ConfigKey<?>> configs = getAllConfigsProduced(InterfaceImplementingProducer.class, "foo");
        assertEquals(2, configs.size());
        assertTrue(configs.contains(new ConfigKey<>(SimpletypesConfig.CONFIG_DEF_NAME, "foo", SimpletypesConfig.CONFIG_DEF_NAMESPACE)));
        assertTrue(configs.contains(new ConfigKey<>(ArraytypesConfig.CONFIG_DEF_NAME, "foo", ArraytypesConfig.CONFIG_DEF_NAMESPACE)));
    }

    @Test
    void getAllConfigsProduced_includes_configs_directly_implemented_by_producer() {
        Set<ConfigKey<?>> configs = getAllConfigsProduced(SimpleProducer.class, "foo");
        assertEquals(1, configs.size());
        assertTrue(configs.contains(new ConfigKey<>(SimpletypesConfig.CONFIG_DEF_NAME, "foo", SimpletypesConfig.CONFIG_DEF_NAMESPACE)));
    }

    @Test
    void requireThatRestartMethodsAreDetectedProperly() {
        assertFalse(ReflectionUtil.hasRestartMethods(NonRestartConfig.class));
        assertTrue(ReflectionUtil.hasRestartMethods(RestartConfig.class));
    }

    @Test
    void requireThatRestartMethodsAreProperlyInvoked() {
        assertTrue(ReflectionUtil.containsFieldsFlaggedWithRestart(RestartConfig.class));
        assertEquals("testing", ReflectionUtil.getChangesRequiringRestart(new RestartConfig(), new RestartConfig()).getName());
    }

    @Test
    void requireThatGetChangesRequiringRestartValidatesParameterTypes() {
        assertThrows(IllegalArgumentException.class, () -> {
            ReflectionUtil.getChangesRequiringRestart(new RestartConfig(), new NonRestartConfig());
        });
    }


}
