// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

/**
 * Wrapper class for the actually measured value. Candidate for removal, but I
 * wanted a type instead of some opaque instance of Number.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
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
