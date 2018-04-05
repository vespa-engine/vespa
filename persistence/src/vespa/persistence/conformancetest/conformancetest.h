// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * This conformance test class has been created in order to run the same tests
 * on multiple implementations of the persistence SPI.
 *
 * To run conformance tests on a given implementation, just add a little wrapper
 * such as the dummy persistence implementation does. (See dummyimpltest.cpp)
 */
#pragma once

#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/vdstestlib/cppunit/macros.h>

// Use an ordering such that the most basic stuff is tested before more advanced
// stuff, such that if there is a catastrophic failure crashing tests, it fails
// on simple operations rather than complex, to ease debugging.
#define DEFINE_CONFORMANCE_TESTS() \
    CPPUNIT_TEST(testBasics); \
    CPPUNIT_TEST(testPut); \
    CPPUNIT_TEST(testPutNewDocumentVersion); \
    CPPUNIT_TEST(testPutOlderDocumentVersion); \
    CPPUNIT_TEST(testPutDuplicate); \
    CPPUNIT_TEST(testRemove); \
    CPPUNIT_TEST(testRemoveMerge); \
    CPPUNIT_TEST(testUpdate); \
    CPPUNIT_TEST(testGet); \
    CPPUNIT_TEST(testIterateCreateIterator); \
    CPPUNIT_TEST(testIterateWithUnknownId); \
    CPPUNIT_TEST(testIterateDestroyIterator); \
    CPPUNIT_TEST(testIterateAllDocs); \
    CPPUNIT_TEST(testIterateAllDocsNewestVersionOnly); \
    CPPUNIT_TEST(testIterateChunked); \
    CPPUNIT_TEST(testMaxByteSize); \
    CPPUNIT_TEST(testIterateMatchTimestampRange); \
    CPPUNIT_TEST(testIterateExplicitTimestampSubset); \
    CPPUNIT_TEST(testIterateRemoves); \
    CPPUNIT_TEST(testIterateMatchSelection); \
    CPPUNIT_TEST(testIterationRequiringDocumentIdOnlyMatching); \
    CPPUNIT_TEST(testIterateBadDocumentSelection); \
    CPPUNIT_TEST(testIterateAlreadyCompleted); \
    CPPUNIT_TEST(testIterateEmptyBucket); \
    CPPUNIT_TEST(testBucketInfo); \
    CPPUNIT_TEST(testOrderIndependentBucketInfo); \
    CPPUNIT_TEST(testDeleteBucket); \
    CPPUNIT_TEST(testSplitNormalCase); \
    CPPUNIT_TEST(testSplitTargetExists); \
    CPPUNIT_TEST(testSplitSingleDocumentInSource); \
    CPPUNIT_TEST(testJoinNormalCase); \
    CPPUNIT_TEST(testJoinNormalCaseWithMultipleBitsDecreased); \
    CPPUNIT_TEST(testJoinOneBucket); \
    CPPUNIT_TEST(testJoinTargetExists); \
    CPPUNIT_TEST(testJoinSameSourceBuckets); \
    CPPUNIT_TEST(testJoinSameSourceBucketsWithMultipleBitsDecreased); \
    CPPUNIT_TEST(testJoinSameSourceBucketsTargetExists); \
    CPPUNIT_TEST(testMaintain); \
    CPPUNIT_TEST(testGetModifiedBuckets); \
    CPPUNIT_TEST(testBucketActivation); \
    CPPUNIT_TEST(testBucketActivationSplitAndJoin); \
    CPPUNIT_TEST(testRemoveEntry); \
    CPPUNIT_TEST(testBucketSpaces); \
    CPPUNIT_TEST(detectAndTestOptionalBehavior);

namespace document
{

class DocumentTypeRepo;
class TestDocMan;

}

namespace document::internal { class InternalDocumenttypesType; }

namespace storage {
namespace spi {

struct ConformanceTest : public CppUnit::TestFixture {
    struct PersistenceFactory {
        typedef std::unique_ptr<PersistenceFactory> UP;
        using DocumenttypesConfig = const document::internal::InternalDocumenttypesType;

        virtual ~PersistenceFactory() {}
        virtual PersistenceProvider::UP getPersistenceImplementation(
                const std::shared_ptr<const document::DocumentTypeRepo> &repo,
                const DocumenttypesConfig &typesCfg) = 0;

        virtual void
        clear(void)
        {
            // clear persistent state, i.e. remove files/directories
        }

        virtual bool
        hasPersistence(void) const
        {
            return false;
        }
        virtual bool
        supportsActiveState() const
        {
            return false;
        }
        virtual bool
        supportsRemoveEntry() const
        {
            return false;
        }
        // If bucket spaces are supported then testdoctype2 is in bucket space 1
        virtual bool supportsBucketSpaces() const { return false; }
    };
    PersistenceFactory::UP _factory;

private:
    void populateBucket(const Bucket& b,
                        PersistenceProvider& spi,
                        Context& context,
                        uint32_t from,
                        uint32_t to,
                        document::TestDocMan& testDocMan);

    void
    testDeleteBucketPostCondition(const PersistenceProvider::UP &spi,
                                  const Bucket &bucket,
                                  const Document &doc1);

    void
    testSplitNormalCasePostCondition(const PersistenceProvider::UP &spi,
                                     const Bucket &bucketA,
                                     const Bucket &bucketB,
                                     const Bucket &bucketC,
                                     document::TestDocMan &testDocMan);

    void
    testSplitTargetExistsPostCondition(const PersistenceProvider::UP &spi,
                                       const Bucket &bucketA,
                                       const Bucket &bucketB,
                                       const Bucket &bucketC,
                                       document::TestDocMan &testDocMan);

    void
    testSplitSingleDocumentInSourcePostCondition(
            const PersistenceProvider::UP& spi,
            const Bucket& source,
            const Bucket& target1,
            const Bucket& target2,
            document::TestDocMan& testDocMan);

    void
    createAndPopulateJoinSourceBuckets(
            PersistenceProvider& spi,
            const Bucket& source1,
            const Bucket& source2,
            document::TestDocMan& testDocMan);

    void
    doTestJoinNormalCase(const Bucket& source1,
                         const Bucket& source2,
                         const Bucket& target);

    void
    testJoinNormalCasePostCondition(const PersistenceProvider::UP &spi,
                                    const Bucket &bucketA,
                                    const Bucket &bucketB,
                                    const Bucket &bucketC,
                                    document::TestDocMan &testDocMan);

    void
    testJoinTargetExistsPostCondition(const PersistenceProvider::UP &spi,
                                      const Bucket &bucketA,
                                      const Bucket &bucketB,
                                      const Bucket &bucketC,
                                      document::TestDocMan &testDocMan);

    void
    testJoinOneBucketPostCondition(const PersistenceProvider::UP &spi,
                                   const Bucket &bucketA,
                                   const Bucket &bucketC,
                                   document::TestDocMan &testDocMan);

    void
    doTestJoinSameSourceBuckets(const Bucket& source,
                                const Bucket& target);

    void
    testJoinSameSourceBucketsPostCondition(
            const PersistenceProvider::UP& spi,
            const Bucket& source,
            const Bucket& target,
            document::TestDocMan& testDocMan);

    void
    testJoinSameSourceBucketsTargetExistsPostCondition(
            const PersistenceProvider& spi,
            const Bucket& source,
            const Bucket& target,
            document::TestDocMan& testDocMan);
public:
    ConformanceTest(PersistenceFactory::UP f) : _factory(std::move(f)) {}

    /**
     * Tests that one can put and remove entries to the persistence
     * implementation, and iterate over the content. This functionality is
     * needed by most other tests in order to verify correct behavior, so
     * this needs to work for other tests to work.
     */
    void testBasics();

    /**
     * Test that listing of buckets works as intended.
     */
    void testListBuckets();

    /**
     * Test that bucket info is generated in a legal fashion. (Such that
     * split/join/merge can work as intended)
     */
    void testBucketInfo();
    /**
     * Test that given a set of operations with certain timestamps, the bucket
     * info is the same no matter what order we feed these in.
     */
    void testOrderIndependentBucketInfo();

    /** Test that the various document operations work as intended. */
    void testPut();
    void testPutNewDocumentVersion();
    void testPutOlderDocumentVersion();
    void testPutDuplicate();
    void testRemove();
    void testRemoveMerge();
    void testUpdate();
    void testGet();

    /** Test that iterating special cases works. */
    void testIterateCreateIterator();
    void testIterateWithUnknownId();
    void testIterateDestroyIterator();
    void testIterateAllDocs();
    void testIterateAllDocsNewestVersionOnly();
    void testIterateChunked();
    void testMaxByteSize();
    void testIterateMatchTimestampRange();
    void testIterateExplicitTimestampSubset();
    void testIterateRemoves();
    void testIterateMatchSelection();
    void testIterationRequiringDocumentIdOnlyMatching();
    void testIterateBadDocumentSelection();
    void testIterateAlreadyCompleted();
    void testIterateEmptyBucket();

    /** Test that the various bucket operations work as intended. */
    void testCreateBucket();
    void testDeleteBucket();
    void testSplitTargetExists();
    void testSplitNormalCase();
    void testSplitSingleDocumentInSource();
    void testJoinNormalCase();
    void testJoinNormalCaseWithMultipleBitsDecreased();
    void testJoinTargetExists();
    void testJoinOneBucket();
    void testJoinSameSourceBuckets();
    void testJoinSameSourceBucketsWithMultipleBitsDecreased();
    void testJoinSameSourceBucketsTargetExists();
    void testMaintain();
    void testGetModifiedBuckets();
    void testBucketActivation();
    void testBucketActivationSplitAndJoin();

    void testRemoveEntry();

    /** Test multiple bucket spaces */
    void testBucketSpaces();

    /**
     * Reports what optional behavior is supported by implementation and not.
     * Tests functionality if supported.
     */
    void detectAndTestOptionalBehavior();
};

} // spi
} // storage

