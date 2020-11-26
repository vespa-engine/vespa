// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.jdisc.Metric;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestMetric implements Metric {

    public final Map<String, Number> values = new LinkedHashMap<>();
    public final Map<String, List<TestContext>> context = new LinkedHashMap<>();

    @Override
    public void set(String key, Number val, Context ctx) {
        values.put(key, val);
        if (ctx != null) {
            //Create one context pr value added - copy the context to not have side effects
            TestContext kontekst = (TestContext)createContext(((TestContext) ctx).properties);
            if (!context.containsKey(key)) {
                context.put(key, new ArrayList<>());
            }
            kontekst.setValue(val);
            context.get(key).add(kontekst);
        }
    }

    @Override
    public void add(String key, Number val, Context ctx) {
        values.put(key, val);
        if (ctx != null) {
            //Create one context pr value added - copy the context to not have side effects
            TestContext copy = (TestContext) createContext(((TestContext) ctx).properties);
            if (!context.containsKey(key)) {
                context.put(key, new ArrayList<>());
            }
            copy.setValue(val);
            context.get(key).add(copy);
        }
    }

    @Override
    public Context createContext(Map<String, ?> properties) {
        return new TestContext(properties);
    }

    double sumDoubleValues(String key, Context sumContext) {
        double sum = 0.0;
        for(Context c : context.getOrDefault(key, List.of())) {
            TestContext tc = (TestContext) c;
            if (tc.value instanceof Double && tc.properties.equals(((TestContext) sumContext).properties)) {
                sum += (double) tc.value;
            }
        }
        return sum;
    }

    double sumNumberValues(String key) {
        double sum = 0.0;
        for(Context c : context.getOrDefault(key, List.of())) {
            TestContext tc = (TestContext) c;
            sum += tc.value.doubleValue();
        }
        return sum;
    }

    public void remove(String key) {
        values.remove(key);
        context.remove(key);
    }

    /**
     * Context where the propertymap is not shared - but unique to each value.
     */
    static class TestContext implements Context{
        Number value;
        final Map<String, ?> properties;

        public TestContext(Map<String, ?> properties) {
            this.properties = properties;
        }

        public void setValue(Number value) {
            this.value = value;
        }
    }

}
