// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "test.h"
#include <vespa/document/test/make_bucket_space.h>

using document::BucketId;
using document::BucketSpace;
using document::test::makeBucketSpace;

namespace storage::spi::test {

Bucket makeSpiBucket(BucketId bucketId)
{
    return Bucket(document::Bucket(makeBucketSpace(), bucketId));
}

}
