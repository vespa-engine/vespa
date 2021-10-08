// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;


import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;


/**
 * A set of sums or other histograms.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class Histogram implements Bucket {
    // The upper and lower limit for the bucket in another histogram
    // this histogram represents. The "outermost" histogram in a
    // multidimensional histogram will effectively ignore this, but set
    // them to -Inf and Inf for consistency.
    private final double lower;
    private final double upper;

    // The names of all the axes, only used in "outermost" histogram in
    // multi dimensional histogram.
    private String axes = null;

    private List<Bucket> buckets = new ArrayList<>();

    /**
     * Build a new histogram using bucket limits from the given Limits
     * object.
     */
    public Histogram(Limits limits) {
        // lower and upper will never be used here,
        // but it's nicer with defined values
        this(limits, 0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

        // generate axis names, but only if there is more than one
        int guard = limits.getDimensions();
        if (guard == 1) {
            return;
        }
        StringBuffer names = new StringBuffer();
        int i = 0;
        while (i < guard) {
            names.append(limits.getAxis(i).getName());
            i++;
            if (i < guard) {
                names.append(",");
            }
        }
        axes = names.toString();
    }

    private Histogram(Limits limits, int dim, double lower, double upper) {
        this.lower = lower;
        this.upper = upper;
        // last axis, create sum objects instead of histograms
        boolean lastAxis = dim == (limits.getDimensions() - 1);
        Axis next = limits.getAxis(dim);
        double[] delimiters = next.getLimits();
        int i = 0;
        double previousBucket = Double.NEGATIVE_INFINITY;
        dim++;
        while (i < delimiters.length) {
            if (lastAxis) {
                buckets.add(new Sum(previousBucket, delimiters[i]));
            } else {
                buckets.add(new Histogram(limits, dim,
                                          previousBucket, delimiters[i]));
            }
            previousBucket = delimiters[i];
            i++;
        }
        if (lastAxis) {
            buckets.add(new Sum(previousBucket, Double.POSITIVE_INFINITY));
        } else {
            buckets.add(new Histogram(limits, dim,
                                      previousBucket,
                                      Double.POSITIVE_INFINITY));
        }

    }

    /**
     * Increment the corresponding bucket for this data point by 1.
     */
    public synchronized void put(double[] value) {
        put(value, 0);
    }

    /**
     * Increment the corresponding bucket for this data point by 1.
     *
     * @param dim the index of the first value to consider in value array
     */
    @Override
    public void put(double[] value, int dim) {
        Bucket bucket = findBucket(0, buckets.size(), value[dim]);
        bucket.put(value, ++dim);
    }

    private Bucket findBucket(int offset, int limit, double value) {
        int index = offset + (limit - offset) / 2;
        Bucket bucket = buckets.get(index);
        if (bucket.lowerLimit() <= value && value < bucket.upperLimit()) {
            return bucket;
        } else if (bucket.upperLimit() <= value) {
            return findBucket(index + 1, limit, value);
        } else { // value < bucket.lowerLimit()
            return findBucket(offset, index, value);
        }
    }

    @Override
    public String toString() {
        StringBuffer s;
        int i, t;

        s = new StringBuffer();
        if (axes != null) {
            s.append(axes).append(" ");
        }
        s.append("(");
        s.append(buckets.get(0).toString());
        s.append(")");

        t = buckets.size();
        i = 1;
        while (i < t) {
            Bucket b = buckets.get(i);
            s.append(" < ");
            s.append(b.lowerLimit());
            s.append(" (");
            s.append(b.toString());
            s.append(")");
            i += 1;
        }

        return s.toString();
    }

    /**
     * Reset all contained buckets.
     */
    @Override
    public void reset() {
        for (Iterator<Bucket> i = buckets.iterator(); i.hasNext(); ) {
            i.next().reset();
        }
    }

    /**
     * The lower limit for the bucket this histogram represents.
     *
     * @return the lower limit for the bucket this histogram represents
     */
    @Override
    public double lowerLimit() {
        return lower;
    }

    /**
     * The upper limit for the bucket this histogram represents.
     *
     * @return the upper limit for the bucket this histogram represents
     */
    @Override
    public double upperLimit() {
        return upper;
    }

    @Override
    public List<Bucket> getBuckets() {
        return buckets;
    }

    private List<Bucket> getLeaves() {
        final class Bookmark {
            final int i;
            final List<Bucket> buckets;
            Bookmark(int i, List<Bucket> buckets) {
                this.i = i;
                this.buckets = buckets;
            }
        }
        List<Bucket> sums = new ArrayList<>();
        Deque<Bookmark> stack = new ArrayDeque<>();
        List<Bucket> current;
        int i = 0;
        stack.addFirst(new Bookmark(i, buckets));
        while (stack.size() > 0) {
            Bookmark currentMark = stack.removeFirst();
            i = currentMark.i;
            current = currentMark.buckets;
            while (i < current.size()) {
                Bucket b = current.get(i++);
                if (b.isLeaf()) {
                    sums.add(b);
                } else {
                    Bookmark marker = new Bookmark(i, current);
                    stack.addFirst(marker);
                    i = 0;
                    current = b.getBuckets();
                }
            }
        }
        return sums;
    }

    void merge(Histogram source) {
        List<Bucket> src = source.getLeaves();
        List<Bucket> dst = getLeaves();
        if (dst.size() != src.size()) {
            throw new IllegalStateException(
                    "Number of buckets in destination and source not equal. (Source "
                            + src.size() + ", destination " + dst.size() + ".");
        }
        for (int i = 0; i < dst.size(); ++i) {
            dst.get(i).add(src.get(i).getSum());
        }
    }

    @Override
    public long getSum() {
        throw new RuntimeException("Not implemented.");
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    @Override
    public void add(long n) {
        throw new IllegalStateException("Can not add directly to a Histogram instance.");
    }
}
