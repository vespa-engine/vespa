// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/storage/persistence/splitbitdetector.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/document/base/testdocman.h>
#include <vespa/document/bucket/bucketidfactory.h>


namespace storage {

namespace {
    spi::LoadType defaultLoadType(0, "default");
}

struct SplitBitDetectorTest : public CppUnit::TestFixture {
    void testSingleUser();
    void testTwoUsers();
    void testMaxBits();
    void testMaxBitsOneBelowMax();
    void testUnsplittable();
    void testUnsplittableMinCount();
    void testEmpty();
    void testZeroDocLimitFallbacksToOneBitIncreaseWith1Doc();
    void testZeroDocLimitFallbacksToOneBitIncreaseOnGidCollision();
    void findBucketCollisionIds();

    spi::DocEntry::UP
    generateDocEntry(uint32_t userId,
                     uint32_t docNum,
                     spi::Timestamp timestamp)
    {
        std::ostringstream ost;
        ost << "id:storage_test:testdoctype1:n=" << userId << ":" << docNum;
        return spi::DocEntry::UP(new spi::DocEntry(
                timestamp, 0, document::DocumentId(ost.str())));
    };

    CPPUNIT_TEST_SUITE(SplitBitDetectorTest);
    CPPUNIT_TEST(testSingleUser);
    CPPUNIT_TEST(testTwoUsers);
    CPPUNIT_TEST(testMaxBits);
    CPPUNIT_TEST(testMaxBitsOneBelowMax);
    CPPUNIT_TEST(testUnsplittable);
    CPPUNIT_TEST(testUnsplittableMinCount);
    CPPUNIT_TEST(testEmpty);
    CPPUNIT_TEST(testZeroDocLimitFallbacksToOneBitIncreaseWith1Doc);
    CPPUNIT_TEST(testZeroDocLimitFallbacksToOneBitIncreaseOnGidCollision);
    CPPUNIT_TEST_DISABLED(findBucketCollisionIds);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(SplitBitDetectorTest);

void
SplitBitDetectorTest::testTwoUsers()
{
    document::TestDocMan testDocMan;
    spi::dummy::DummyPersistence provider(testDocMan.getTypeRepoSP(), 1);
    provider.getPartitionStates();
    spi::Bucket bucket(document::BucketId(1, 1),
                       spi::PartitionId(0));
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));

    provider.createBucket(bucket, context);

    std::vector<spi::DocEntry::UP> entries;
    for (uint32_t i = 0; i < 5; ++i) {
        document::Document::SP doc(
                testDocMan.createRandomDocumentAtLocation(1, i, 1, 1));
        provider.put(bucket, spi::Timestamp(1000 + i), doc, context);
    }

    for (uint32_t i = 5; i < 10; ++i) {
        document::Document::SP doc(
                testDocMan.createRandomDocumentAtLocation(3, i, 1, 1));
        provider.put(bucket, spi::Timestamp(1000 + i), doc, context);
    }

    SplitBitDetector::Result result(
            SplitBitDetector::detectSplit(provider, bucket, 58, context));
    CPPUNIT_ASSERT_EQUAL(
            std::string("SplitTargets(2: BucketId(0x0800000000000001), "
                        "BucketId(0x0800000000000003))"),
            result.toString());
}

void
SplitBitDetectorTest::testSingleUser()
{
    document::TestDocMan testDocMan;
    spi::dummy::DummyPersistence provider(testDocMan.getTypeRepoSP(), 1);
    provider.getPartitionStates();
    spi::Bucket bucket(document::BucketId(1, 1),
                       spi::PartitionId(0));
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));

    provider.createBucket(bucket, context);

    std::vector<spi::DocEntry::UP> entries;
    for (uint32_t i = 0; i < 10; ++i) {
        document::Document::SP doc(
                testDocMan.createRandomDocumentAtLocation(1, i, 1, 1));
        provider.put(bucket, spi::Timestamp(1000 + i), doc, context);
    }

    SplitBitDetector::Result result(
            SplitBitDetector::detectSplit(provider, bucket, 58, context));
    CPPUNIT_ASSERT_EQUAL(
            std::string("SplitTargets(33: BucketId(0x8400000000000001), "
                        "BucketId(0x8400000100000001))"),
            result.toString());
}

void
SplitBitDetectorTest::testMaxBits()
{
    document::TestDocMan testDocMan;
    spi::dummy::DummyPersistence provider(testDocMan.getTypeRepoSP(), 1);
    provider.getPartitionStates();
    spi::Bucket bucket(document::BucketId(1, 1),
                       spi::PartitionId(0));
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    int minContentSize = 1, maxContentSize = 1;

    provider.createBucket(bucket, context);

    std::vector<spi::DocEntry::UP> entries;
    for (uint32_t seed = 0; seed < 10; ++seed) {
        int location = 1;
        document::Document::SP doc(testDocMan.createRandomDocumentAtLocation(
                location, seed, minContentSize, maxContentSize));
        provider.put(bucket, spi::Timestamp(1000 + seed), doc, context);
    }

    SplitBitDetector::Result result(
            SplitBitDetector::detectSplit(provider, bucket, 3, context));
    CPPUNIT_ASSERT_EQUAL(
            std::string("SplitTargets(3: BucketId(0x0c00000000000001), "
                        "[ BucketId(0x0c00000000000005) ])"),
            result.toString());
}

void
SplitBitDetectorTest::testMaxBitsOneBelowMax()
{
    document::TestDocMan testDocMan;
    spi::dummy::DummyPersistence provider(testDocMan.getTypeRepoSP(), 1);
    provider.getPartitionStates();
    spi::Bucket bucket(document::BucketId(15, 1), spi::PartitionId(0));
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    int minContentSize = 1, maxContentSize = 1;

    provider.createBucket(bucket, context);

    std::vector<spi::DocEntry::UP> entries;
    for (uint32_t seed = 0; seed < 10; ++seed) {
        int location = 1 | (seed % 2 == 0 ? 0x8000 : 0);
        document::Document::SP doc(testDocMan.createRandomDocumentAtLocation(
                location, seed, minContentSize, maxContentSize));
        provider.put(bucket, spi::Timestamp(1000 + seed), doc, context);
    }

    //std::cerr << provider.dumpBucket(bucket) << "\n";

    SplitBitDetector::Result result(
            SplitBitDetector::detectSplit(provider, bucket, 15, context));
    CPPUNIT_ASSERT_EQUAL(
            std::string("SplitTargets(error: No use in trying to split "
                        "Bucket(0x3c00000000000001, partition 0) when max split"
                        " bit is set to 15.)"),
            result.toString());

    result = SplitBitDetector::detectSplit(provider, bucket, 16, context);
    CPPUNIT_ASSERT_EQUAL(
            std::string("SplitTargets(16: BucketId(0x4000000000000001), "
                        "BucketId(0x4000000000008001))"),
            result.toString());
}

void
SplitBitDetectorTest::testUnsplittable()
{
    document::TestDocMan testDocMan;
    spi::dummy::DummyPersistence provider(testDocMan.getTypeRepoSP(), 1);
    provider.getPartitionStates();
    spi::Bucket bucket(document::BucketId(1, 1),
                       spi::PartitionId(0));
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));

    provider.createBucket(bucket, context);

    std::vector<spi::DocEntry::UP> entries;

    for (uint32_t i = 0; i < 10; ++i) {
        document::Document::SP doc(
                testDocMan.createRandomDocumentAtLocation(1, 1, 1, 1));
        provider.put(bucket, spi::Timestamp(1000 + i), doc, context);
    }

    SplitBitDetector::Result result(
            SplitBitDetector::detectSplit(provider, bucket, 58, context, 100));
    CPPUNIT_ASSERT_EQUAL(
            std::string("SplitTargets(58: BucketId(0xe94c074f00000001), "
                        "BucketId(0xeb4c074f00000001))"),
            result.toString());
}

void
SplitBitDetectorTest::testUnsplittableMinCount()
{
    document::TestDocMan testDocMan;
    spi::dummy::DummyPersistence provider(testDocMan.getTypeRepoSP(), 1);
    provider.getPartitionStates();
    spi::Bucket bucket(document::BucketId(1, 1),
                       spi::PartitionId(0));
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));

    provider.createBucket(bucket, context);

    std::vector<spi::DocEntry::UP> entries;

    for (uint32_t i = 0; i < 10; ++i) {
        document::Document::SP doc(
                testDocMan.createRandomDocumentAtLocation(1, 1, 1, 1));
        provider.put(bucket, spi::Timestamp(1000 + i), doc, context);
    }

    SplitBitDetector::Result result(
            SplitBitDetector::detectSplit(provider, bucket, 58, context, 5, 0));
    // Still no other choice than split out to 58 bits regardless of minCount.
    CPPUNIT_ASSERT_EQUAL(
            std::string("SplitTargets(58: BucketId(0xe94c074f00000001), "
                        "BucketId(0xeb4c074f00000001))"),
            result.toString());
}


void
SplitBitDetectorTest::testEmpty()
{
    document::TestDocMan testDocMan;
    spi::dummy::DummyPersistence provider(testDocMan.getTypeRepoSP(), 1);
    provider.getPartitionStates();
    spi::Bucket bucket(document::BucketId(1, 1),
                       spi::PartitionId(0));
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));

    provider.createBucket(bucket, context);

    SplitBitDetector::Result result(
            SplitBitDetector::detectSplit(provider, bucket, 58, context));
    CPPUNIT_ASSERT_EQUAL(
            std::string("SplitTargets(source empty)"),
            result.toString());
}

void
SplitBitDetectorTest::testZeroDocLimitFallbacksToOneBitIncreaseWith1Doc()
{
    document::TestDocMan testDocMan;
    spi::dummy::DummyPersistence provider(testDocMan.getTypeRepoSP(), 1);
    provider.getPartitionStates();
    spi::Bucket bucket(document::BucketId(1, 1),
                       spi::PartitionId(0));
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));

    provider.createBucket(bucket, context);
    document::Document::SP doc(
            testDocMan.createRandomDocumentAtLocation(1, 0, 1, 1));
    provider.put(bucket, spi::Timestamp(1000), doc, context);

    SplitBitDetector::Result result(
            SplitBitDetector::detectSplit(provider, bucket, 58, context, 0, 0));
    CPPUNIT_ASSERT_EQUAL(
            std::string("SplitTargets(2: BucketId(0x0800000000000001), "
                        "BucketId(0x0800000000000003))"),
            result.toString());
}

void
SplitBitDetectorTest::testZeroDocLimitFallbacksToOneBitIncreaseOnGidCollision()
{
    document::TestDocMan testDocMan;
    spi::dummy::DummyPersistence provider(testDocMan.getTypeRepoSP(), 1);
    provider.getPartitionStates();
    spi::Bucket bucket(document::BucketId(1, 1),
                       spi::PartitionId(0));
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));

    provider.createBucket(bucket, context);
    document::Document::SP doc(
            testDocMan.createRandomDocumentAtLocation(1, 0, 1, 1));
    provider.put(bucket, spi::Timestamp(1000), doc, context);
    provider.put(bucket, spi::Timestamp(2000), doc, context);

    SplitBitDetector::Result result(
            SplitBitDetector::detectSplit(provider, bucket, 58, context, 0, 0));
    CPPUNIT_ASSERT_EQUAL(
            std::string("SplitTargets(2: BucketId(0x0800000000000001), "
                        "BucketId(0x0800000000000003))"),
            result.toString());
}

/**
 * Not a regular unit test in itself, but more of an utility to find non-unique
 * document IDs that map to the same 58-bit bucket ID. Disabled by default since
 * it costs CPU to do this and is not necessary during normal testing.
 */
void
SplitBitDetectorTest::findBucketCollisionIds()
{
    using document::DocumentId;
    using document::BucketId;

    document::BucketIdFactory factory;

    DocumentId targetId("id:foo:music:n=123456:ABCDEFGHIJKLMN");
    BucketId targetBucket(factory.getBucketId(targetId));
    char candidateSuffix[] = "ABCDEFGHIJKLMN";

    size_t iterations = 0;
    constexpr size_t maxIterations = 100000000;
    while (std::next_permutation(std::begin(candidateSuffix),
                                 std::end(candidateSuffix) - 1))
    {
        ++iterations;

        DocumentId candidateId(
                vespalib::make_string("id:foo:music:n=123456:%s",
                                      candidateSuffix));
        BucketId candidateBucket(factory.getBucketId(candidateId));
        if (targetBucket == candidateBucket) {
            std::cerr << "\nFound a collision after " << iterations
                      << " iterations!\n"
                      << "target:    " << targetId << " -> " << targetBucket
                      << "\ncollision: " << candidateId << " -> "
                      << candidateBucket << "\n";
            return;
        }

        if (iterations == maxIterations) {
            std::cerr << "\nNo collision found after " << iterations
                      << " iterations :[\n";
            return;
        }
    }
    std::cerr << "\nRan out of permutations after " << iterations
              << " iterations!\n";
}

}
