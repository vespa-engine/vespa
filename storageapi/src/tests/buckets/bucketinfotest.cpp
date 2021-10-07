// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageapi/buckets/bucketinfo.h>
#include <gtest/gtest.h>

namespace storage::api {

/** Tests simple operations */
TEST(BucketInfoTest, testSimple)
{
    BucketInfo info;

    EXPECT_FALSE(info.valid());
    EXPECT_EQ(0u, info.getChecksum());
    EXPECT_EQ(0u, info.getDocumentCount());
    EXPECT_EQ(1u, info.getTotalDocumentSize());

    info.setChecksum(0xa000bbbb);
    info.setDocumentCount(15);
    info.setTotalDocumentSize(64000);

    EXPECT_TRUE(info.valid());
    EXPECT_EQ(0xa000bbbb, info.getChecksum());
    EXPECT_EQ(15u, info.getDocumentCount());
    EXPECT_EQ(64000u, info.getTotalDocumentSize());
};

}
