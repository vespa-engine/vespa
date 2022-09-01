// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.foo.AppConfig;
import com.yahoo.foo.TestNonstringConfig;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests different aspects of the ConfigInstance class and its underlying Nodes.
 *
 * @author gjoranv
 */
public class ConfigInstanceTest {

    private final ConfigSourceSet sourceSet = new ConfigSourceSet("config-instance-test");

    /**
     * Verifies that the subscriber's configure() method is only
     * called once upon subscribe, even if there are more than one
     * subscribers to the same ConfigInstance. This has previously
     * been a problem, since ConfigInstance.subscribe() called
     * configureSubscriber(), which configures all subscribers to the
     * instance. Now, the new method configureSubscriber(Subscriber)
     * is called instead.
     */
    @Test
    public void testConfigureOnlyOnceUponSubscribe() {
        final String configId = "raw:times 1\n";
        AppService service1 = new AppService(configId, sourceSet);
        AppService service2 = new AppService(configId, sourceSet);

        assertEquals(1, service1.timesConfigured());
        assertEquals(1, service2.timesConfigured());

        service1.cancelSubscription();
        service2.cancelSubscription();
    }

    /**
     * Verifies that values set in previous setConfig() calls are
     * retained when the payload in a new setConfig() call does not
     * overwrite them.
     */
    @Test
    @Ignore
    public void testRetainOldValuesOnConfigUpdates() {
        AppConfig config = new AppConfig(new AppConfig.Builder());
        //config.setConfig(Arrays.asList("message \"one\"", "times 333"), "", 0L);
        assertEquals("one", config.message());
        assertEquals(333, config.times());

        //config.setConfig(Arrays.asList("message \"two\""), "", 0L);
        assertEquals("two", config.message());
        assertEquals("config.times retains previously set value", 333, config.times());

        //config.setConfig(Arrays.asList("times 666"), "", 0L);
        assertEquals("config.message retains previously set value", "two", config.message());
        assertEquals(666, config.times());
    }

    /**
     * Verifies that an exception is thrown when one attempts to set an
     * illegal config value for parameters that have default values.
     */
    @Test
    public void testFailUponIllegalValue() {
        verifyIllegalValue("i notAnInt");
        verifyIllegalValue("i 3.0");

        verifyIllegalValue("d noDouble");
        verifyIllegalValue("d 3.0.");

        // verifyIllegalValue("b notTrueOrFalse");
        //verifyIllegalValue("b 1");
        //verifyIllegalValue("b 0");

        verifyIllegalValue("e undeclaredEnumValue");
        verifyIllegalValue("e 0");
    }

    private void verifyIllegalValue(String line) {
        String configId = "raw:" + line + "\n";
        try {
            new TestNonstring(configId);
            fail("Expected ConfigurationRuntimeException when setting a parameter value of wrong type.");
        } catch (RuntimeException expected) {
            verifyException(expected, "Not able to create config builder for payload", "Got ConfigurationRuntimeException for the wrong reason. " +
                    "Expected to fail when setting a parameter value of wrong type.");
        }

    }

    private void verifyException(Throwable throwable, String expected, String failMessage) {
        Throwable t = throwable;
        boolean ok = false;
        while (t != null) {
            if (t.getMessage() != null && t.getMessage().contains(expected)) {
                ok = true;
                break;
            }
            t = t.getCause();
        }
        if (!ok) {
            throwable.printStackTrace();
            fail(failMessage);
        }
    }

    private class TestNonstring {

        public TestNonstring(String configId) {
            ConfigSubscriber subscriber = new ConfigSubscriber();
            ConfigHandle<TestNonstringConfig> handle = subscriber.subscribe(TestNonstringConfig.class, configId);
            subscriber.nextConfig(false);
            handle.getConfig();
        }
    }

}
