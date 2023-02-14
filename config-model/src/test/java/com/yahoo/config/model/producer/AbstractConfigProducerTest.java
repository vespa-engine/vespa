// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.producer;

import com.yahoo.cloud.config.log.LogdConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies some of the logic in the abstract config producer that is not tested in other classes.
 *
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class AbstractConfigProducerTest {

    @Test
    void require_that_interface_is_found_if_directly_implemented() throws ReflectiveOperationException {
        MockLogdProducer producer = new MockLogdProducer("mocky");
        ClassLoader loader = producer.getConfigClassLoader(LogdConfig.Producer.class.getName());
        assertNotNull(loader);
        Class<?> clazz = loader.loadClass(LogdConfig.Builder.class.getName());
        LogdConfig.Builder builder = (LogdConfig.Builder) clazz.getDeclaredConstructor().newInstance();
        producer.getConfig(builder);
        LogdConfig config = new LogdConfig(builder);
        assertEquals("bar", config.logserver().host());
        assertEquals(1338, config.logserver().rpcport());
    }

    @Test
    void require_that_interface_is_found_if_inherited() throws ReflectiveOperationException {
        MockLogdProducerSubclass producer = new MockLogdProducerSubclass("mocky");
        ClassLoader loader = producer.getConfigClassLoader(LogdConfig.Producer.class.getName());
        assertNotNull(loader);
        Class<?> clazz = loader.loadClass(LogdConfig.Builder.class.getName());
        LogdConfig.Builder builder = (LogdConfig.Builder) clazz.getDeclaredConstructor().newInstance();
        producer.getConfig(builder);
        LogdConfig config = new LogdConfig(builder);
        assertEquals("foo", config.logserver().host());
        assertEquals(1337, config.logserver().rpcport());
    }

    private static class MockLogdProducer extends TreeConfigProducer implements LogdConfig.Producer {

        public MockLogdProducer(String subId) {
            super(subId);
        }

        @Override
        public void getConfig(LogdConfig.Builder builder) {
            builder.logserver(new LogdConfig.Logserver.Builder().host("bar").rpcport(1338));
        }
    }

    private static abstract class MockLogdSuperClass extends TreeConfigProducer implements LogdConfig.Producer {

        public MockLogdSuperClass(String subId) {
            super(subId);
        }
    }

    private static class MockLogdProducerSubclass extends MockLogdSuperClass {
        public MockLogdProducerSubclass(String subId) {
            super(subId);
        }

        @Override
        public void getConfig(LogdConfig.Builder builder) {
            builder.logserver(new LogdConfig.Logserver.Builder().host("foo").rpcport(1337));
        }
    }
}
