// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "test.h"

using document::BucketId;
using document::BucketSpace;

namespace storage::spi::test {

BucketSpace makeBucketSpace()
{
    return BucketSpace::placeHolder();
}

BucketSpace makeBucketSpace(const vespalib::string &docTypeName)
{
    // Used by persistence conformance test to map fron document type name
    // to bucket space.  See document::TestDocRepo for known document types.
    if (docTypeName == "no") {
        return BucketSpace(2);
    } else if (docTypeName == "testdoctype2") {
        return BucketSpace(1);
    } else {
        return makeBucketSpace();
    }
}

Bucket makeBucket(BucketId bucketId, PartitionId partitionId)
{
    return Bucket(document::Bucket(BucketSpace::placeHolder(), bucketId), partitionId);
}

Bucket makeBucket(BucketId bucketId)
{
    return makeBucket(bucketId, PartitionId(0));
}

}
