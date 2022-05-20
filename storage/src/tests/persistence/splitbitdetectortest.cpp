// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/storage/persistence/splitbitdetector.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/document/base/testdocman.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <algorithm>

using storage::spi::test::makeSpiBucket;
using namespace ::testing;

namespace storage {

using DocEntryList = std::vector<spi::DocEntry::UP>;

struct SplitBitDetectorTest : Test {
    document::TestDocMan testDocMan;
    spi::dummy::DummyPersistence provider;
    spi::Bucket bucket;
    spi::Context context;

    SplitBitDetectorTest()
        : testDocMan(),
          provider(testDocMan.getTypeRepoSP()),
          bucket(makeSpiBucket(document::BucketId(1, 1))),
          context(spi::Priority(0), spi::Trace::TraceLevel(0))
    {
        provider.initialize();
        provider.createBucket(bucket);
    }
};

TEST_F(SplitBitDetectorTest, two_users) {
    DocEntryList entries;
    for (uint32_t i = 0; i < 5; ++i) {
        document::Document::SP doc(testDocMan.createRandomDocumentAtLocation(1, i, 1, 1));
        provider.put(bucket, spi::Timestamp(1000 + i), doc);
    }

    for (uint32_t i = 5; i < 10; ++i) {
        document::Document::SP doc(testDocMan.createRandomDocumentAtLocation(3, i, 1, 1));
        provider.put(bucket, spi::Timestamp(1000 + i), doc);
    }

    SplitBitDetector::Result result(SplitBitDetector::detectSplit(provider, bucket, 58, context));
    EXPECT_EQ("SplitTargets(2: BucketId(0x0800000000000001), "
              "BucketId(0x0800000000000003))",
              result.toString());
}

TEST_F(SplitBitDetectorTest, single_user) {
    DocEntryList entries;
    for (uint32_t i = 0; i < 10; ++i) {
        document::Document::SP doc(testDocMan.createRandomDocumentAtLocation(1, i, 1, 1));
        provider.put(bucket, spi::Timestamp(1000 + i), doc);
    }

    SplitBitDetector::Result result(SplitBitDetector::detectSplit(provider, bucket, 58, context));
    EXPECT_EQ("SplitTargets(33: BucketId(0x8400000000000001), "
              "BucketId(0x8400000100000001))",
              result.toString());
}

TEST_F(SplitBitDetectorTest, max_bits) {
    int minContentSize = 1, maxContentSize = 1;

    DocEntryList entries;
    for (uint32_t seed = 0; seed < 10; ++seed) {
        int location = 1;
        document::Document::SP doc(testDocMan.createRandomDocumentAtLocation(location, seed, minContentSize, maxContentSize));
        provider.put(bucket, spi::Timestamp(1000 + seed), doc);
    }

    SplitBitDetector::Result result(SplitBitDetector::detectSplit(provider, bucket, 3, context));
    EXPECT_EQ("SplitTargets(3: BucketId(0x0c00000000000001), "
              "[ BucketId(0x0c00000000000005) ])",
              result.toString());
}

TEST_F(SplitBitDetectorTest, max_bits_one_below_max) {
    spi::Bucket my_bucket(makeSpiBucket(document::BucketId(15, 1)));
    int minContentSize = 1, maxContentSize = 1;

    provider.createBucket(my_bucket);

    DocEntryList entries;
    for (uint32_t seed = 0; seed < 10; ++seed) {
        int location = 1 | (seed % 2 == 0 ? 0x8000 : 0);
        document::Document::SP doc(testDocMan.createRandomDocumentAtLocation(location, seed, minContentSize, maxContentSize));
        provider.put(my_bucket, spi::Timestamp(1000 + seed), doc);
    }

    SplitBitDetector::Result result(
            SplitBitDetector::detectSplit(provider, my_bucket, 15, context));
    EXPECT_EQ("SplitTargets(error: No use in trying to split "
              "Bucket(0x3c00000000000001) when max split"
              " bit is set to 15.)",
              result.toString());

    result = SplitBitDetector::detectSplit(provider, my_bucket, 16, context);
    EXPECT_EQ("SplitTargets(16: BucketId(0x4000000000000001), "
              "BucketId(0x4000000000008001))",
              result.toString());
}

TEST_F(SplitBitDetectorTest, unsplittable) {
    DocEntryList entries;

    for (uint32_t i = 0; i < 10; ++i) {
        document::Document::SP doc(testDocMan.createRandomDocumentAtLocation(1, 1, 1, 1));
        provider.put(bucket, spi::Timestamp(1000 + i), doc);
    }

    SplitBitDetector::Result result(SplitBitDetector::detectSplit(provider, bucket, 58, context, 100));
    EXPECT_EQ("SplitTargets(58: BucketId(0xe94c074f00000001), "
              "BucketId(0xeb4c074f00000001))",
              result.toString());
}

TEST_F(SplitBitDetectorTest, unsplittable_min_count) {
    DocEntryList entries;

    for (uint32_t i = 0; i < 10; ++i) {
        document::Document::SP doc(testDocMan.createRandomDocumentAtLocation(1, 1, 1, 1));
        provider.put(bucket, spi::Timestamp(1000 + i), doc);
    }

    SplitBitDetector::Result result(SplitBitDetector::detectSplit(provider, bucket, 58, context, 5, 0));
    // Still no other choice than split out to 58 bits regardless of minCount.
    EXPECT_EQ("SplitTargets(58: BucketId(0xe94c074f00000001), "
              "BucketId(0xeb4c074f00000001))",
              result.toString());
}

TEST_F(SplitBitDetectorTest, empty) {
    SplitBitDetector::Result result(SplitBitDetector::detectSplit(provider, bucket, 58, context));
    EXPECT_EQ("SplitTargets(source empty)", result.toString());
}

TEST_F(SplitBitDetectorTest, zero_doc_limit_falls_back_to_one_bit_increase_with_1_doc) {
    document::Document::SP doc(testDocMan.createRandomDocumentAtLocation(1, 0, 1, 1));
    provider.put(bucket, spi::Timestamp(1000), doc);

    SplitBitDetector::Result result(SplitBitDetector::detectSplit(provider, bucket, 58, context, 0, 0));
    EXPECT_EQ("SplitTargets(2: BucketId(0x0800000000000001), "
              "BucketId(0x0800000000000003))",
              result.toString());
}

TEST_F(SplitBitDetectorTest, zero_doc_limit_falls_back_to_one_bit_increase_on_gid_collision) {
    document::Document::SP doc(testDocMan.createRandomDocumentAtLocation(1, 0, 1, 1));
    provider.put(bucket, spi::Timestamp(1000), doc);
    provider.put(bucket, spi::Timestamp(2000), doc);

    SplitBitDetector::Result result(SplitBitDetector::detectSplit(provider, bucket, 58, context, 0, 0));
    EXPECT_EQ("SplitTargets(2: BucketId(0x0800000000000001), "
              "BucketId(0x0800000000000003))",
              result.toString());
}

}
