// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi;

import com.yahoo.document.BucketId;

/**
 * @author thomasg
 */
public class Bucket {
    BucketId bucketId;
    short partitionId;

    /**
     * @param partition The partition (i.e. disk) where the bucket is located
     * @param bucketId The bucket id of the bucket
     */
    public Bucket(short partition, BucketId bucketId) {
        this.partitionId = partition;
        this.bucketId = bucketId;
    }

    public BucketId getBucketId() { return bucketId; }

    public short getPartitionId() { return partitionId; }

    @Override
    public String toString() {
        return partitionId + "/" + bucketId;
    }
}
