// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Simon Thoresen Hult
 */
public class BucketDistribution {

    // A logger object to enable proper logging.
    private static final Logger log = Logger.getLogger(BucketDistribution.class.getName());

    // A map from bucket id to column index.
    private int[] bucketToColumn;

    // The number of columns to distribute to.
    private int numColumns;

    // The number of bits to use for bucket identification.
    private int numBucketBits;

    /**
     * Constructs a new bucket distribution object with a given number of columns and buckets.
     *
     * @param numColumns    The number of columns to distribute to.
     * @param numBucketBits The number of bits to use for bucket id.
     */
    public BucketDistribution(int numColumns, int numBucketBits) {
        this.numBucketBits = numBucketBits;
        bucketToColumn = new int[getNumBuckets()];
        reset();
        setNumColumns(numColumns);
    }

    /**
     * Constructs a new bucket distribution object as a copy of another.
     *
     * @param other The distribution object to copy.
     */
    public BucketDistribution(BucketDistribution other) {
        bucketToColumn = other.bucketToColumn.clone();
        numColumns = other.numColumns;
        numBucketBits = other.numBucketBits;
    }

    /**
     * Returns the number of buckets that the given number of bucket bits will allow.
     *
     * @param numBucketBits The number of bits to use for bucket id.
     * @return The number of buckets allowed.
     */
    private static int getNumBuckets(int numBucketBits) {
        return 1 << numBucketBits;
    }

    /**
     * This method returns a list that contains the distributions of the given number of buckets over the given number
     * of columns.
     *
     * @param numColumns    The number of columns to distribute to.
     * @param numBucketBits The number of bits to use for bucket id.
     * @return The bucket distribution.
     */
    private static List<Integer> getBucketCount(int numColumns, int numBucketBits) {
        List<Integer> ret = new ArrayList<Integer>(numColumns);
        int cnt = getNumBuckets(numBucketBits) / numColumns;
        int rst = getNumBuckets(numBucketBits) % numColumns;
        for (int i = 0; i < numColumns; ++i) {
            ret.add(cnt + (i < rst ? 1 : 0));
        }
        return ret;
    }

    /**
     * This method returns a list similar to {@link BucketDistribution#getBucketCount(int, int)}, except that the returned list
     * contains the number of buckets that will have to be migrated from each column if an additional column was added.
     *
     * @param numColumns    The original number of columns.
     * @param numBucketBits The number of bits to use for bucket id.
     * @return The number of buckets to migrate, one value per column.
     */
    private static List<Integer> getBucketMigrateCount(int numColumns, int numBucketBits) {
        List<Integer> ret = getBucketCount(numColumns++, numBucketBits);
        int cnt = getNumBuckets(numBucketBits) / numColumns;
        int rst = getNumBuckets(numBucketBits) % numColumns;
        for (int i = 0; i < numColumns - 1; ++i) {
            ret.set(i, ret.get(i) - (cnt + (i < rst ? 1 : 0)));
        }
        return ret;
    }

    /**
     * Sets the number of columns to distribute to to 1, and resets the content of the internal bucket-to-column map so
     * that it all buckets point to that single column.
     */
    public void reset() {
        for (int i = 0; i < bucketToColumn.length; ++i) {
            bucketToColumn[i] = 0;
        }
        numColumns = 1;
    }

    /**
     * Adds a single column to this bucket distribution object. This will modify the internal bucket-to-column map so
     * that it takes into account the new column.
     */
    private void addColumn() {
        int newColumns = numColumns + 1;
        List<Integer> migrate = getBucketMigrateCount(numColumns, numBucketBits);
        int numBuckets = getNumBuckets(numBucketBits);
        for (int i = 0; i < numBuckets; ++i) {
            int old = bucketToColumn[i];
            if (migrate.get(old) > 0) {
                bucketToColumn[i] = numColumns; // move this bucket to the new column
                migrate.set(old, migrate.get(old) - 1);
            }
        }
        numColumns = newColumns;
    }

    /**
     * Sets the number of columns to use for this document distribution object. This will reset and setup this object
     * from scratch. The original number of buckets is maintained.
     *
     * @param numColumns The new number of columns to distribute to.
     */
    public synchronized void setNumColumns(int numColumns) {
        if (numColumns < this.numColumns) {
            reset();
        }
        if (numColumns == this.numColumns) {
            return;
        }
        for (int i = numColumns - this.numColumns; --i >= 0; ) {
            addColumn();
        }
    }

    /**
     * Returns the number of columns to distribute to.
     *
     * @return The number of columns.
     */
    public int getNumColumns() {
        return numColumns;
    }

    /**
     * Sets the number of buckets to use for this document distribution object. This will reset and setup this object
     * from scratch. The original number of columns is maintained.
     *
     * @param numBucketBits The new number of bits to use for bucket id.
     */
    public synchronized void setNumBucketBits(int numBucketBits) {
        if (numBucketBits == this.numBucketBits) {
            return;
        }
        this.numBucketBits = numBucketBits;
        bucketToColumn = new int[getNumBuckets(numBucketBits)];
        int numColumns = this.numColumns;
        reset();
        setNumColumns(numColumns);
    }

    /**
     * Returns the number of bits used for bucket identifiers.
     *
     * @return The number of bits.
     */
    public int getNumBucketBits() {
        return numBucketBits;
    }

    /**
     * Returns the number of buckets available using the configured number of bucket bits.
     *
     * @return The number of buckets.
     */
    public int getNumBuckets() {
        return getNumBuckets(numBucketBits);
    }

    /**
     * This method maps the given bucket id to its corresponding column.
     *
     * @param bucketId The bucket whose column to lookup.
     * @return The column to distribute the bucket to.
     */
    public int getColumn(BucketId bucketId) {
        int ret = (int)(bucketId.getId() & (getNumBuckets(numBucketBits) - 1));
        if (ret >= bucketToColumn.length) {
            log.log(Level.SEVERE,
                    "The bucket distribution map is not in sync with the number of bucket bits. " +
                    "This should never happen! Distribution is broken!!");
            return 0;
        }
        return bucketToColumn[ret];
    }
}
