// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/util/sync.h>
#include <vector>

namespace vdslib {

/**
 * Stable algorithmic hash distribution; this class assigns hash buckets to targets. The number of hash buckets should
 * be large compared to the number of targets. The mapping from hash value to hash bucket is performed outside this
 * class.
 */
class BucketDistribution {
public:
    /**
     * Constructs a new bucket distribution object with a given number of columns and buckets.
     *
     * @param numColumns    The number of columns to distribute to.
     * @param numBucketBits The number of bits to use for bucket id.
     */
    BucketDistribution(uint32_t numColumns, uint32_t numBucketBits);
    ~BucketDistribution();

    /**
     * Returns the number of buckets that the given number of bucket bits will allow.
     *
     * @param numBucketBits The number of bits to use for bucket id.
     * @return              The number of buckets allowed.
     */
    static uint32_t getNumBuckets(uint32_t numBucketBits) { return 1 << numBucketBits; }

    /**
     * This method returns a list that contains the distributions of the given number of buckets over the given number
     * of columns.
     *
     * @param numColumns    The number of columns to distribute to.
     * @param numBucketBits The number of bits to use for bucket id.
     * @param ret           List to fill with the bucket distribution.
     */
    static void getBucketCount(uint32_t numColumns, uint32_t numBucketBits, std::vector<uint32_t> &ret);

    /**
     * This method returns a list similar to {@link this#getBucketCount(int,int)}, except that the returned list
     * contains the number of buckets that will have to be migrated from each column if an additional column was added.
     *
     * @param numColumns    The original number of columns.
     * @param numBucketBits The number of bits to use for bucket id.
     * @param ret           List to fill with the number of buckets to migrate, one value per column.
     */
    static void getBucketMigrateCount(uint32_t numColumns, uint32_t numBucketBits, std::vector<uint32_t> &ret);

    /**
     * Sets the number of columns to distribute to to 1, and resets the content of the internal bucket-to-column map so
     * that it all buckets point to that single column.
     */
    void reset();

    /**
     * Sets the number of columns to use for this document distribution object. This will reset and setup this object
     * from scratch. The original number of buckets is maintained.
     *
     * @param numColumns The new number of columns to distribute to.
     */
    void setNumColumns(uint32_t numColumns);

    /**
     * Returns the number of columns to distribute to.
     *
     * @return The number of columns.
     */
    uint32_t getNumColumns() const { return _numColumns; }

    /**
     * Sets the number of buckets to use for this document distribution object. This will reset and setup this object
     * from scratch. The original number of columns is maintained.
     *
     * @param numBucketBits The new number of bits to use for bucket id.
     */
    void setNumBucketBits(uint32_t numBucketBits);

    /**
     * Returns the number of bits used for bucket identifiers.
     *
     * @return The number of bits.
     */
    uint32_t getNumBucketBits() const { return _numBucketBits; }

    /**
     * Returns the number of buckets available using the configured number of bucket bits.
     *
     * @return The number of buckets.
     */
    uint32_t getNumBuckets() const { return getNumBuckets(_numBucketBits); }

    /**
     * This method maps the given bucket id to its corresponding column.
     *
     * @param bucketId The bucket whose column to lookup.
     * @return         The column to distribute the bucket to.
     */
    uint32_t getColumn(const document::BucketId &bucketId) const;

private:
    /**
     * Adds a single column to this bucket distribution object. This will modify the internal bucket-to-column map so
     * that it takes into account the new column.
     */
    void addColumn();

private:
    uint32_t              _numColumns;     // The number of columns to distribute to.
    uint32_t              _numBucketBits;  // The number of bits to use for bucket identification.
    std::vector<uint32_t> _bucketToColumn; // A map from bucket id to column index.
    vespalib::Lock        _lock;
};

}
