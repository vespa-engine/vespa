// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import java.time.Instant;
import java.util.Optional;

/**
 * @author olaa
 */
public class MetricsAggregator {

    double feedLatencySum;
    double feedLatencyCount;
    double qrQueryLatencySum;
    double qrQueryLatencyCount;
    double containerQueryLatencySum;
    double containerQueryLatencyCount;
    double documentCount;
    Instant timestamp;

    public void addFeedLatencySum(double feedLatencySum) {
        this.feedLatencySum += feedLatencySum;
    }

    public void addFeedLatencyCount(double feedLatencyCount) {
        this.feedLatencyCount += feedLatencyCount;
    }

    public void addQrQueryLatencyCount(double qrQueryLatencyCount) {
        this.qrQueryLatencyCount += qrQueryLatencyCount;
    }

    public void addQrQueryLatencySum(double qrQueryLatencySum) {
        this.qrQueryLatencySum += qrQueryLatencySum;
    }

    public void addContainerQueryLatencyCount(double containerQueryLatencyCount) {
        this.containerQueryLatencyCount += containerQueryLatencyCount;
    }

    public void addContainerQueryLatencySum(double containerQueryLatencySum) {
        this.containerQueryLatencySum += containerQueryLatencySum;
    }

    public void addDocumentCount(double documentCount) {
        this.documentCount += documentCount;
    }

    public Optional<Double> aggregateFeedLatency() {
        if (isZero(feedLatencySum) || isZero(feedLatencyCount)) return Optional.empty();
        return Optional.of(feedLatencySum / feedLatencyCount);
    }

    public Optional<Double> aggregateFeedRate() {
        if (isZero(feedLatencyCount)) return Optional.empty();
        return Optional.of(feedLatencyCount / 60);
    }

    public Optional<Double> aggregateQueryLatency() {
        if (isZero(containerQueryLatencyCount, containerQueryLatencySum) && isZero(qrQueryLatencyCount, qrQueryLatencySum)) return Optional.empty();
        return Optional.of((containerQueryLatencySum + qrQueryLatencySum) / (containerQueryLatencyCount + qrQueryLatencyCount));
    }

    public Optional<Double> aggregateQueryRate() {
        if (isZero(containerQueryLatencyCount) && isZero(qrQueryLatencyCount)) return Optional.empty();
        return Optional.of((containerQueryLatencyCount + qrQueryLatencyCount) / 60);
    }

    public Optional<Double> aggregateDocumentCount() {
        if (isZero(documentCount)) return Optional.empty();
        return Optional.of(documentCount);
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    private boolean isZero(double... values) {
        boolean isZero = false;
        for (double value : values) {
            isZero |= Math.abs(value) < 0.001;
        }
        return isZero;
    }

}
