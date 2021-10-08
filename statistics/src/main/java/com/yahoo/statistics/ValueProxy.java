// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;


/**
 * To be able to cache events concerning Values internally, group them
 * together and similar.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
class ValueProxy extends Proxy {
    private double raw;
    private boolean hasRaw = false;
    private double min;
    private boolean hasMin = false;
    private double mean;
    private boolean hasMean = false;
    private double max;
    private boolean hasMax = false;
    private Histogram histogram;
    private boolean hasHistogram;

    ValueProxy(String name) {
        super(name);
    }

    boolean hasRaw() {
        return hasRaw;
    }
    double getRaw() {
        return raw;
    }
    void setRaw(double raw) {
        hasRaw = true;
        this.raw = raw;
    }

    boolean hasMin() {
        return hasMin;
    }
    double getMin() {
        return min;
    }
    void setMin(double min) {
        hasMin = true;
        this.min = min;
    }

    boolean hasMean() {
        return hasMean;
    }
    double getMean() {
        return mean;
    }
    void setMean(double mean) {
        hasMean = true;
        this.mean = mean;
    }

    boolean hasMax() {
        return hasMax;
    }
    double getMax() {
        return max;
    }
    void setMax(double max) {
        hasMax = true;
        this.max = max;
    }

    boolean hasHistogram() {
        return hasHistogram;
    }
    Histogram getHistogram() {
        return histogram;
    }
    void setHistogram(Histogram histogram) {
        hasHistogram = true;
        this.histogram = histogram;
    }

}

