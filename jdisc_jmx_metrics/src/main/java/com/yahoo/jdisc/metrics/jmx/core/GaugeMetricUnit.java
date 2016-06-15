// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx.core;

import java.util.*;

/**
 * <p>This class represents a gauge value that can be safely written to by one thread, and read from another thread.
 * Every time that the gauge value is requested, the average value of all the {@link Number}s in the list is returned.
 * This approach is preferred over keeping a single {@link Number} to avoid overflowing.</p>
 *
 * <p>It is heavily based off the Vespa implementation of ThreadRobustList, which does not involve any locking or
 * use of volatile. It does not support multiple writes from multiple threads, but that is fine in our case.
 *
 * <p>Note: If an instance of the iterator is created and another thread calls {@link #addValue}, the iterator might
 * <i>not</i> see the new value. However, we guarantee that all elements up to the exit of the Iterator constructor
 * will be seen.</p>
 *
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public final class GaugeMetricUnit implements MetricUnit {

    private Number[] items;

    // Index of next item
    private int next = 0;

    public GaugeMetricUnit(int initialCapacity) {
        items = new Number[initialCapacity];
    }

    @Override
    public Number getValue() {
        GaugeMetricIterator iterator = new GaugeMetricIterator(items);
        double sum = 0; // overflow should not be an issue for reasonable intervals
        int count = 0;
        while (iterator.hasNext()) {
            sum += iterator.next().doubleValue();
            count++;
        }
        if (count <= 0) {
            return null;
        }
        return sum / count;
    }

    @Override
    public void addValue(Number value) {
        value.getClass();  // throws NullPointerException
        Number[] curItems = items;
        if (next >= items.length) {
            final int newLength = 20 + items.length * 2;
            curItems = Arrays.copyOf(curItems, newLength);
            curItems[next++] = value;
            items = curItems;
        } else {
            curItems[next++] = value;
        }
    }

    /**
     * Copies the elements from the argument. Does not remove any element from the argument
     */
    @Override
    public void addMetric(MetricUnit metricUnit) {
        if (! (metricUnit instanceof GaugeMetricUnit)) {
            throw new IllegalArgumentException(metricUnit.getClass().getName());
        }
        GaugeMetricIterator iterator = new GaugeMetricIterator(((GaugeMetricUnit)metricUnit).items);
        while (iterator.hasNext()) {
            addValue(iterator.next());
        }
    }

    @Override
    public boolean isPersistent() {
      return false;
    }

    private class GaugeMetricIterator implements Iterator<Number> {

        // 'final' ensures safe publication and to be up-to-date up to the point where constructor exits
        private final Number[] items;

        private int nextIndex = 0;

        private GaugeMetricIterator(final Number[] items) {
            items.getClass(); // throws NullPointerException
            this.items = items;
        }

        @Override
        public boolean hasNext() {
            if (nextIndex >= items.length) {
                return false;
            }
            if (items[nextIndex] == null) {
                return false;
            }
            return true;
        }

        @Override
        public Number next() {
            if (! hasNext()) {
                throw new NoSuchElementException();
            }
            return items[nextIndex++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

}
