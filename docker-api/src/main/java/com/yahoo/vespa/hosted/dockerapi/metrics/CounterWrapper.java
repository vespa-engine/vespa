package com.yahoo.vespa.hosted.dockerapi.metrics;

import com.yahoo.metrics.simple.Counter;

/**
 * @author valerijf
 */
public class CounterWrapper implements MetricValue {
    private final Counter counter;
    private long value = 0;

    CounterWrapper(Counter counter) {
        this.counter = counter;
    }

    public void add() {
        counter.add();
        value++;
    }

    public void add(long n) {
        counter.add(n);
        value += n;
    }

    public Number getValue() {
        return value;
    }


    static final class NullCounter extends CounterWrapper {
        NullCounter() {
            super(null);
        }

        @Override
        public void add() {
        }

        public void add(long n) {
        }
    }
}