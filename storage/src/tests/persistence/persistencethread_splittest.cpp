// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/persistence/persistencethread.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <tests/persistence/persistencetestutils.h>

namespace storage {
namespace {
    spi::LoadType defaultLoadType(0, "default");
}

struct PersistenceThread_SplitTest : public SingleDiskPersistenceTestUtils
{
    enum SplitCase {
        TOO_MANY_DOCS_SPLIT_ONCE, // Only one split needed to divide
        TOO_MANY_DOCS_SPLIT_MULTIPLE_BITS, // Multiple bits needed to divide
        TOO_MANY_DOCS_ACTUALLY_NOT, // Other copy is too big but not this one
                                    // Multi bits needed, but dont do it.
        TOO_LARGE_DOCS_SPLIT_ONCE,
        TOO_LARGE_DOCS_SPLIT_MULTIPLE_BITS,
        TOO_LARGE_DOCS_SINGLE_DOC, // Cannot split single doc even if too large
        TOO_LARGE_DOCS_ACTUALLY_NOT, // Other copy is too large, not this one
            // Need to split to X bits to get in line with other copy or distr.
        SPLIT_TOO_LITTLE_SINGLE_SPLIT, // Split all to one target
        SPLIT_TOO_LITTLE_JUST_RIGHT, // Just manage to split in two at that lvl
        SPLIT_TOO_LITTLE_SPLIT_TOWARDS_ENOUGH, // Has to split shorter
        SPLIT_INCONSISTENT_1_DOC,
        SPLIT_INCONSISTENT_ALL_DOCS_SAME_GID,
    };

    void doTest(SplitCase);

    void testTooManyDocsSplitOnce()
        { doTest(TOO_MANY_DOCS_SPLIT_ONCE); }
    void testTooManyDocsSplitMulti()
        { doTest(TOO_MANY_DOCS_SPLIT_MULTIPLE_BITS); }
    void testTooManyDocsActuallyNot()
        { doTest(TOO_MANY_DOCS_ACTUALLY_NOT); }
    void testTooLargeDocsSplitOnce()
        { doTest(TOO_LARGE_DOCS_SPLIT_ONCE); }
    void testTooLargeDocsSplitMulti()
        { doTest(TOO_LARGE_DOCS_SPLIT_MULTIPLE_BITS); }
    void testTooLargeDocsSingleDoc()
        { doTest(TOO_LARGE_DOCS_SINGLE_DOC); }
    void testTooLargeDocsActuallyNot()
        { doTest(TOO_LARGE_DOCS_ACTUALLY_NOT); }
    void testSplitTooLittleSingleSplit()
        { doTest(SPLIT_TOO_LITTLE_SINGLE_SPLIT); }
    void testSplitTooLittleJustRight()
        { doTest(SPLIT_TOO_LITTLE_JUST_RIGHT); }
    void testSplitTooLittleSplitTowardsEnough()
        { doTest(SPLIT_TOO_LITTLE_SPLIT_TOWARDS_ENOUGH); }
    void testInconsistentSplitHasOneBitFallbackWhen1Doc() {
        doTest(SPLIT_INCONSISTENT_1_DOC);
    }
    void testInconsistentSplitHasOneBitFallbackWhenAllDocsHaveSameGid() {
        doTest(SPLIT_INCONSISTENT_ALL_DOCS_SAME_GID);
    }

    CPPUNIT_TEST_SUITE(PersistenceThread_SplitTest);
    CPPUNIT_TEST(testTooManyDocsSplitOnce);
    CPPUNIT_TEST(testTooManyDocsSplitMulti);
    CPPUNIT_TEST(testTooManyDocsActuallyNot);
    CPPUNIT_TEST(testTooLargeDocsSplitOnce);
    CPPUNIT_TEST(testTooLargeDocsSplitMulti);
    CPPUNIT_TEST(testTooLargeDocsSingleDoc);
    CPPUNIT_TEST(testTooLargeDocsActuallyNot);
    CPPUNIT_TEST(testSplitTooLittleSingleSplit);
    CPPUNIT_TEST(testSplitTooLittleJustRight);
    CPPUNIT_TEST(testSplitTooLittleSplitTowardsEnough);
    CPPUNIT_TEST(testInconsistentSplitHasOneBitFallbackWhen1Doc);
    CPPUNIT_TEST(testInconsistentSplitHasOneBitFallbackWhenAllDocsHaveSameGid);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(PersistenceThread_SplitTest);

void
PersistenceThread_SplitTest::doTest(SplitCase splitCase)
{
    uint32_t maxCount = 4;
    uint32_t maxSize = 1000 * 1000;
    uint32_t maxBits = 58;
    uint32_t minBits = 1;
    uint32_t docCount = 8;
    uint32_t docSize = 100 * 1000;
    uint32_t currentSplitLevel = 1;
    uint32_t splitLevelToDivide = 2;
    uint32_t resultSplitLevel = 2;
    size_t resultBuckets = 2;
    bool simulateGidCollision = false;
    api::ReturnCode error(api::ReturnCode::OK);
    switch (splitCase) {
        case TOO_MANY_DOCS_SPLIT_ONCE:
            break; // Default. Do nothing
        case TOO_MANY_DOCS_SPLIT_MULTIPLE_BITS:
            splitLevelToDivide = 3;
            resultSplitLevel = 3;
            break;
        case TOO_MANY_DOCS_ACTUALLY_NOT:
            splitLevelToDivide = 3;
            docCount = 2;
            resultBuckets = 1;
            break;
        case TOO_LARGE_DOCS_SPLIT_ONCE:
            maxCount = 100;
            docSize = 400 * 1000;
            break;
        case TOO_LARGE_DOCS_SPLIT_MULTIPLE_BITS:
            maxCount = 100;
            docSize = 400 * 1000;
            splitLevelToDivide = 3;
            resultSplitLevel = 3;
            break;
        case TOO_LARGE_DOCS_SINGLE_DOC:
                // It is possible for bucket to be inconsistent being big enough
                // to split in other copy but this copy has only 1 too big doc.
            docCount = 1;
            docSize = 3000 * 1000;
            splitLevelToDivide = 3;
            resultBuckets = 1;
            break;
        case TOO_LARGE_DOCS_ACTUALLY_NOT:
            maxCount = 100;
            splitLevelToDivide = 3;
            resultSplitLevel = 2;
            resultBuckets = 1;
            break;
        case SPLIT_TOO_LITTLE_SINGLE_SPLIT:
            maxBits = 5;
            maxSize = 0;
            maxCount = 0;
            splitLevelToDivide = 16;
            resultSplitLevel = 5;
            resultBuckets = 1;
            break;
        case SPLIT_TOO_LITTLE_JUST_RIGHT:
            maxBits = 5;
            maxSize = 0;
            maxCount = 0;
            splitLevelToDivide = 5;
            resultSplitLevel = 5;
            break;
        case SPLIT_TOO_LITTLE_SPLIT_TOWARDS_ENOUGH:
            maxBits = 8;
            maxSize = 0;
            maxCount = 0;
            splitLevelToDivide = 5;
            resultSplitLevel = 5;
            break;
        case SPLIT_INCONSISTENT_1_DOC:
            docCount = 1;
            maxSize = 0;
            maxCount = 0;
            currentSplitLevel = 16;
            resultSplitLevel = 17;
            resultBuckets = 1;
            break;
        case SPLIT_INCONSISTENT_ALL_DOCS_SAME_GID:
            docCount = 2;
            maxSize = 0;
            maxCount = 0;
            currentSplitLevel = 16;
            resultSplitLevel = 17;
            resultBuckets = 1;
            simulateGidCollision = true;
            break;
        default:
            assert(false);
    }

    uint64_t location = 0;
    uint64_t splitMask = 1 << (splitLevelToDivide - 1);
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    spi::Bucket bucket(document::BucketId(currentSplitLevel, 1),
                       spi::PartitionId(0));
    spi::PersistenceProvider& spi(getPersistenceProvider());
    spi.deleteBucket(bucket, context);
    spi.createBucket(bucket, context);
    document::TestDocMan testDocMan;
    for (uint32_t i=0; i<docCount; ++i) {
        uint64_t docloc;
        uint32_t seed;
        if (!simulateGidCollision) {
            docloc = location | (i % 2 == 0 ? 0 : splitMask);
            seed = i;
        } else {
            docloc = location;
            seed = 0;
        }
        document::Document::SP doc(testDocMan.createRandomDocumentAtLocation(
                docloc, seed, docSize, docSize));
        spi.put(bucket, spi::Timestamp(1000 + i), doc, context);
    }

    std::unique_ptr<PersistenceThread> thread(createPersistenceThread(0));
    getNode().getStateUpdater().setClusterState(
            lib::ClusterState::CSP(
                    new lib::ClusterState("distributor:1 storage:1")));
    api::SplitBucketCommand cmd(document::BucketId(currentSplitLevel, 1));
    cmd.setMaxSplitBits(maxBits);
    cmd.setMinSplitBits(minBits);
    cmd.setMinByteSize(maxSize);
    cmd.setMinDocCount(maxCount);
    cmd.setSourceIndex(0);
    MessageTracker::UP result(thread->handleSplitBucket(cmd));
    api::ReturnCode code(result->getResult());
    CPPUNIT_ASSERT_EQUAL(error, code);
    if (!code.success()) return;
    api::SplitBucketReply& reply(
            dynamic_cast<api::SplitBucketReply&>(*result->getReply()));
    std::set<std::string> expected;
    for (uint32_t i=0; i<resultBuckets; ++i) {
        document::BucketId b(resultSplitLevel,
                             location | (i == 0 ? 0 : splitMask));
        std::ostringstream ost;
        ost << b << " - " << b.getUsedBits();
        expected.insert(ost.str());
    }
    std::set<std::string> actual;
    for (uint32_t i=0; i<reply.getSplitInfo().size(); ++i) {
        std::ostringstream ost;
        document::BucketId b(reply.getSplitInfo()[i].first);
        ost << b << " - " << b.getUsedBits();
        actual.insert(ost.str());
    }
    CPPUNIT_ASSERT_EQUAL(expected, actual);
}

} // storage

