// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

/**
 * Wrapper class for the actually measured value.
 *
 * @author Steinar Knutsen
 */
public class Measurement {

    private final Number magnitude;

    public Measurement(Number magnitude) {
        this.magnitude = magnitude;
    }

    Number getMagnitude() {
        return magnitude;
    }

}
