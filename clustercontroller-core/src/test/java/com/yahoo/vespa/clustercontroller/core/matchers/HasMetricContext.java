// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.matchers;

import com.yahoo.vespa.clustercontroller.utils.util.MetricReporter;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.mockito.ArgumentMatcher;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HasMetricContext extends ArgumentMatcher<MetricReporter.Context> {

    private final Map<String, ?> dimensions;

    private HasMetricContext(Map<String, String> dimensions) {
        this.dimensions = new TreeMap<>(dimensions);
    }

    @Override
    public boolean matches(Object o) {
        if (!(o instanceof MockContext)) {
            return false;
        }
        return dimensions.equals(((MockContext)o).dimensions);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(String.format("Context with dimensions %s", dimensions.toString()));
    }

    @Override
    public void describeMismatch(Object item, Description description) {
        description.appendText(String.format("Context dimensions are %s", item.toString()));
    }

    public static class Dimension {
        final String name;
        final String value;

        private Dimension(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    public static class MockContext implements MetricReporter.Context {
        final TreeMap<String, ?> dimensions;

        public MockContext(Map<String, ?> dimensions) {
            this.dimensions = new TreeMap<>(dimensions);
        }

        @Override
        public String toString() {
            return dimensions.toString();
        }
    }

    public static Dimension withDimension(String name, String value) {
        return new Dimension(name, value);
    }

    @Factory
    public static HasMetricContext hasMetricContext(Dimension... dimensions) {
        return new HasMetricContext(Stream.of(dimensions).collect(Collectors.toMap(dim -> dim.name, dim -> dim.value)));
    }
}
