// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apputil.communication.http;

import com.yahoo.jdisc.Metric;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JDiscMetricWrapperTest {

    class MetricImpl implements Metric {
        int calls = 0;
        @Override
        public void set(String s, Number number, Context context) { ++calls; }
        @Override
        public void add(String s, Number number, Context context) { ++calls; }
        @Override
        public Context createContext(Map<String, ?> stringMap) {
            ++calls;
            return new Context() {};
        }
    }

    @Test
    void testSimple() {
        MetricImpl impl1 = new MetricImpl();
        MetricImpl impl2 = new MetricImpl();
        JDiscMetricWrapper wrapper = new JDiscMetricWrapper(impl1);
        wrapper.add("foo", 234, null);
        wrapper.set("bar", 234, null);
        assertTrue(wrapper.createContext(null) != null);
        assertEquals(3, impl1.calls);
        impl1.calls = 0;
        wrapper.updateMetricImplementation(impl2);
        wrapper.add("foo", 234, wrapper.createContext(null));
        wrapper.set("bar", 234, wrapper.createContext(null));
        assertTrue(wrapper.createContext(null) != null);
        assertEquals(0, impl1.calls);
        assertEquals(5, impl2.calls);

    }

}
