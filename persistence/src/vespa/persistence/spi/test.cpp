// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "test.h"

namespace storage::spi::test {

Bucket makeBucket(document::BucketId bucketId, PartitionId partitionId)
{
    return Bucket(document::Bucket(document::BucketSpace::placeHolder(), bucketId), partitionId);
}

Bucket makeBucket(document::BucketId bucketId)
{
    return makeBucket(bucketId, PartitionId(0));
}

}
