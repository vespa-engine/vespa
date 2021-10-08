// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;

import java.util.List;


/**
 * A bucket in a multidimensional histogram.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
interface Bucket {
    void put(double[] value, int dim);
    void reset();
    double lowerLimit();
    double upperLimit();
    boolean isLeaf();
    List<Bucket> getBuckets();
    long getSum();
    void add(long n);
}
