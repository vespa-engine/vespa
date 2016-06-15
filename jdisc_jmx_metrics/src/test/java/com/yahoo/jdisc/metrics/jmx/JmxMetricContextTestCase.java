// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx;

import com.yahoo.jdisc.metrics.jmx.cloud.JmxMetricConfig;
import org.testng.annotations.Test;

import javax.management.ObjectName;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import static org.testng.AssertJUnit.*;

/**
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public class JmxMetricContextTestCase {

    @Test
    public void requireThatEqualsWorks() {
        JmxMetricContext context = createContext();
        JmxMetricContext context2 = createContext();
        assertTrue(context.equals(context2));
    }

    @Test
    public void requireThatEqualsWorksOnlyForRightType() {
        JmxMetricContext context = createContext();
        assertFalse(context.equals(null));
        assertFalse(context.equals(new Integer(0)));
    }

    @Test
    public void requireThatGetObjectNameWorks() throws Exception {
        JmxMetricContext context = createContext();
        Hashtable<String, String> dimensions = new Hashtable<String, String>();
        dimensions.put("key1", "value1");
        dimensions.put("key2", "value2");

        assertTrue(context.getObjectName().equals(new ObjectName(new JmxMetricConfig(new JmxMetricConfig.Builder()).objectNameDomain(),
                                                                 dimensions)));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void requireThatNullParamIsInvalid() {
        JmxMetricContext context = new JmxMetricContext(new JmxMetricConfig(new JmxMetricConfig.Builder()), null);
        context.getObjectName();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void requireThatInvalidDomainNameThrowsException() {
        JmxMetricConfig.Builder builder = new JmxMetricConfig.Builder();
        builder.objectNameDomain("abc--86^%#@:");
        JmxMetricConfig config = new JmxMetricConfig(builder);
        JmxMetricContext context = new JmxMetricContext(config, new HashMap<String, String>());
        context.getObjectName();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void requireThatInvalidDomainNameThrowsExceptionWithDimensions() {
        JmxMetricConfig.Builder builder = new JmxMetricConfig.Builder();
        builder.objectNameDomain("abc--86^%#@:");
        JmxMetricConfig config = new JmxMetricConfig(builder);
        Hashtable<String, String> dimensions = new Hashtable<String, String>();
        dimensions.put("key1", "value1");
        dimensions.put("key2", "value2");
        JmxMetricContext context = new JmxMetricContext(config, dimensions);
        context.getObjectName();
    }

    @Test
    public void requireThatHashCodeWorks() {
        JmxMetricContext context = createContext();
        assertEquals(-218969104, context.hashCode());
    }

    private JmxMetricContext createContext() {
        Map<String, String> dimensions = new HashMap<String, String>();
        dimensions.put("key1", "value1");
        dimensions.put("key2", "value2");
        return new JmxMetricContext(new JmxMetricConfig(new JmxMetricConfig.Builder()), dimensions);
    }

}
