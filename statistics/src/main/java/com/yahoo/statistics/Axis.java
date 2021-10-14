// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;

import java.util.Arrays;


/**
 * A wrapper class for representing a single axis of an n-dimensional
 * histogram.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
class Axis {
    private final double[] limits;
    private final String name;

    Axis(String name, double[] limits) {
        this.limits = Arrays.copyOf(limits, limits.length);
        this.name = name;
    }

    double[] getLimits() {
        return limits;
    }

    String getName() {
        return name;
    }

}
