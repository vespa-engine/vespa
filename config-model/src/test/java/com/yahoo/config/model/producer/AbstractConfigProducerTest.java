// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.producer;

import com.yahoo.cloud.config.log.LogdConfig;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * Verifies some of the logic in the abstract config producer that is not tested in other classes.
 *
 * @author lulf
 * @since 5.1
 */
public class AbstractConfigProducerTest {

    @Test
    public void require_that_interface_is_found_if_directly_implemented() throws ReflectiveOperationException {
        MockLogdProducer producer = new MockLogdProducer("mocky");
        ClassLoader loader = producer.getConfigClassLoader(LogdConfig.Producer.class.getName());
        assertNotNull(loader);
        Class<?> clazz = loader.loadClass(LogdConfig.Builder.class.getName());
        LogdConfig.Builder builder = (LogdConfig.Builder) clazz.getDeclaredConstructor().newInstance();
        producer.getConfig(builder);
        LogdConfig config = new LogdConfig(builder);
        assertThat(config.logserver().host(), is("bar"));
        assertThat(config.logserver().port(), is(1338));
    }

    @Test
    public void require_that_interface_is_found_if_inherited() throws ReflectiveOperationException {
        MockLogdProducerSubclass producer = new MockLogdProducerSubclass("mocky");
        ClassLoader loader = producer.getConfigClassLoader(LogdConfig.Producer.class.getName());
        assertNotNull(loader);
        Class<?> clazz = loader.loadClass(LogdConfig.Builder.class.getName());
        LogdConfig.Builder builder = (LogdConfig.Builder) clazz.getDeclaredConstructor().newInstance();
        producer.getConfig(builder);
        LogdConfig config = new LogdConfig(builder);
        assertThat(config.logserver().host(), is("foo"));
        assertThat(config.logserver().port(), is(1337));
    }

    private static class MockLogdProducer extends AbstractConfigProducer implements LogdConfig.Producer {

        public MockLogdProducer(String subId) {
            super(subId);
        }

        @Override
        public void getConfig(LogdConfig.Builder builder) {
            builder.logserver(new LogdConfig.Logserver.Builder().host("bar").port(1338));
        }
    }

    private static abstract class MockLogdSuperClass extends AbstractConfigProducer implements LogdConfig.Producer {

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
            builder.logserver(new LogdConfig.Logserver.Builder().host("foo").port(1337));
        }
    }
}
