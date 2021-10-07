// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;


import java.util.List;
import java.util.ArrayList;


/**
 * Limits for a histogram, this is only a wrapper for initializing
 * a histogram.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class Limits {
    private final List<Axis> axes = new ArrayList<>(1);
    private boolean frozen = false;

    /**
     * Create an empty Limits instance.
     */
    public Limits() {
    }

    /**
     * Create a Limits instance suitable for use in a Value.Parameters instance.
     * The instance will be frozen.
     */
    public Limits(double[] limits) {
        addAxis(null, limits);
        freeze();
    }

    /**
     * @param name the name of the variable for this axis
     * @param limits the bucket limits for this axis
     */
    public void addAxis(String name, double[] limits) {
        if (frozen) {
            throw new IllegalStateException("Can not add more axes to a frozen Limits instance.");
        }
        axes.add(new Axis(name, limits));
    }

    int getDimensions() {
        return axes.size();
    }

    Axis getAxis(int i) {
        return axes.get(i);
    }

    /**
     * Prevent adding any mores axes.
     */
    public void freeze() {
        this.frozen = true;
    }

    /**
     * True if further change is not permitted.
     *
     * @return whether this object now should be considered immutable
     */
    public boolean isFrozen() {
        return frozen;
    }

}
