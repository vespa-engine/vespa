// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.util;

import org.junit.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;

public class MetricReporterTest {

    static class MetricReporterMock implements MetricReporter {
        StringBuilder sb = new StringBuilder();

        @Override
        public void set(String s, Number number, Context context) {
            sb.append("set(").append(s).append(", ").append(number).append(")\n");
        }

        @Override
        public void add(String s, Number number, Context context) {
            sb.append("add(").append(s).append(", ").append(number).append(")\n");
        }
        @Override
        public Context createContext(Map<String, ?> stringMap) {
            sb.append("createContext(");
            for (String s : stringMap.keySet()) {
                sb.append(" ").append(s).append("=").append(stringMap.get(s));
            }
            sb.append(" )\n");
            return new Context() {};
        }
    };

    @Test
    public void testNoMetricReporter() {
        NoMetricReporter reporter = new NoMetricReporter();
        reporter.add("foo", 3, null);
        reporter.set("foo", 3, null);
        reporter.createContext(null);
    }

    @Test
    public void testPrefix() {
        MetricReporterMock mock = new MetricReporterMock();
        ComponentMetricReporter c = new ComponentMetricReporter(mock, "prefix");
        c.addDimension("urk", "fy");
        c.add("foo", 2);
        c.set("bar", 1);
        assertEquals(
                "createContext( )\n" +
                "createContext( urk=fy )\n" +
                "add(prefixfoo, 2)\n" +
                "set(prefixbar, 1)\n", mock.sb.toString());

    }

    @Test
    public void testWithContext() {
        MetricReporterMock mock = new MetricReporterMock();
        ComponentMetricReporter c = new ComponentMetricReporter(mock, "prefix");
        c.addDimension("urk", "fy");
        Map<String, Integer> myContext = new TreeMap<>();
        myContext.put("myvar", 3);
        c.add("foo", 2, c.createContext(myContext));
        c.set("bar", 1, c.createContext(myContext));
        assertEquals(
                "createContext( )\n" +
                "createContext( urk=fy )\n" +
                "createContext( myvar=3 urk=fy )\n" +
                "add(prefixfoo, 2)\n" +
                "createContext( myvar=3 urk=fy )\n" +
                "set(prefixbar, 1)\n", mock.sb.toString());
    }

    @Test
    public void testDefaultContext() {
        MetricReporterMock mock = new MetricReporterMock();
        ComponentMetricReporter c = new ComponentMetricReporter(mock, "prefix");
        c.addDimension("urk", "fy");
        c.add("foo", 2, c.createContext(null));
        assertEquals(
                "createContext( )\n" +
                "createContext( urk=fy )\n" +
                "add(prefixfoo, 2)\n", mock.sb.toString());
    }

    @Test
    public void testContextOverlap() {
        MetricReporterMock mock = new MetricReporterMock();
        ComponentMetricReporter c = new ComponentMetricReporter(mock, "prefix");
        c.addDimension("urk", "fy");
        Map<String, String> myContext = new TreeMap<>();
        myContext.put("urk", "yes");
        c.add("foo", 2, c.createContext(myContext));
        assertEquals(
                "createContext( )\n" +
                "createContext( urk=fy )\n" +
                "createContext( urk=yes )\n" +
                "add(prefixfoo, 2)\n", mock.sb.toString());
    }

}
