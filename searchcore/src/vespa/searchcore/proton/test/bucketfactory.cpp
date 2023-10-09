// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketfactory.h"
#include <vespa/persistence/spi/test.h>

using document::BucketId;
using document::DocumentId;
using storage::spi::Bucket;
using storage::spi::test::makeSpiBucket;

namespace proton {

BucketId
BucketFactory::getBucketId(const DocumentId &docId)
{
    BucketId bId = docId.getGlobalId().convertToBucketId();
    bId.setUsedBits(getNumBucketBits());
    return bId;
}


Bucket
BucketFactory::getBucket(const DocumentId &docId)
{
    return makeSpiBucket(getBucketId(docId));
}

} // namespace proton
