// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.service.model;

import com.yahoo.jdisc.Timer;

import java.util.function.Consumer;

public class LatencyMeasurement implements AutoCloseable {
    private final Timer timer;
    private Consumer<Double> elapsedSecondsConsumer;
    private long startMillis;

    LatencyMeasurement(Timer timer, Consumer<Double> elapsedSecondsConsumer) {
        this.timer = timer;
        this.elapsedSecondsConsumer = elapsedSecondsConsumer;
    }

    LatencyMeasurement start() {
        startMillis = timer.currentTimeMillis();
        return this;
    }

    @Override
    public void close() {
        if (elapsedSecondsConsumer != null) {
            double elapsedSeconds = forceNonNegative(timer.currentTimeMillis() - startMillis) / 1000;
            elapsedSecondsConsumer.accept(elapsedSeconds);
            elapsedSecondsConsumer = null;
        }
    }

    private static double forceNonNegative(double d) {
        return d > 0 ? d : 0;
    }
}
