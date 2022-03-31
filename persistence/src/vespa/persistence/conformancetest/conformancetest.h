// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * This conformance test class has been created in order to run the same tests
 * on multiple implementations of the persistence SPI.
 *
 * To run conformance tests on a given implementation, just add a little wrapper
 * such as the dummy persistence implementation does. (See dummyimpltest.cpp)
 */
#pragma once

#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <gtest/gtest.h>

namespace document
{

class DocumentTypeRepo;
class TestDocMan;

}

namespace storage::spi {

class ConformanceTest : public ::testing::Test {

public:
    using PersistenceProviderUP = std::unique_ptr<PersistenceProvider>;
    struct PersistenceFactory {
        typedef std::unique_ptr<PersistenceFactory> UP;

        virtual ~PersistenceFactory() = default;
        virtual PersistenceProviderUP getPersistenceImplementation(
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

    // Set by test runner.
    static std::unique_ptr<PersistenceFactory>(*_factoryFactory)(const std::string &docType);

protected:
    PersistenceFactory::UP _factory;

    void populateBucket(const Bucket& b,
                        PersistenceProvider& spi,
                        uint32_t from,
                        uint32_t to,
                        document::TestDocMan& testDocMan);

    void
    testDeleteBucketPostCondition(const PersistenceProvider &spi,
                                  const Bucket &bucket,
                                  const Document &doc1);

    void
    testSplitNormalCasePostCondition(const PersistenceProvider &spi,
                                     const Bucket &bucketA,
                                     const Bucket &bucketB,
                                     const Bucket &bucketC,
                                     document::TestDocMan &testDocMan);

    void
    testSplitTargetExistsPostCondition(const PersistenceProvider &spi,
                                       const Bucket &bucketA,
                                       const Bucket &bucketB,
                                       const Bucket &bucketC,
                                       document::TestDocMan &testDocMan);

    void
    testSplitSingleDocumentInSourcePostCondition(
            const PersistenceProvider& spi,
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
    testJoinNormalCasePostCondition(const PersistenceProvider &spi,
                                    const Bucket &bucketA,
                                    const Bucket &bucketB,
                                    const Bucket &bucketC,
                                    document::TestDocMan &testDocMan);

    void
    testJoinTargetExistsPostCondition(const PersistenceProvider &spi,
                                      const Bucket &bucketA,
                                      const Bucket &bucketB,
                                      const Bucket &bucketC,
                                      document::TestDocMan &testDocMan);

    void
    testJoinOneBucketPostCondition(const PersistenceProvider &spi,
                                   const Bucket &bucketA,
                                   const Bucket &bucketC,
                                   document::TestDocMan &testDocMan);

    void
    doTestJoinSameSourceBuckets(const Bucket& source,
                                const Bucket& target);

    void
    testJoinSameSourceBucketsPostCondition(
            const PersistenceProvider& spi,
            const Bucket& source,
            const Bucket& target,
            document::TestDocMan& testDocMan);

    void
    testJoinSameSourceBucketsTargetExistsPostCondition(
            const PersistenceProvider& spi,
            const Bucket& source,
            const Bucket& target,
            document::TestDocMan& testDocMan);

    void test_iterate_empty_or_missing_bucket(bool bucket_exists);

    void test_empty_bucket_info(bool bucket_exists, bool active);

    ConformanceTest();
    ConformanceTest(const std::string &docType);
};

class SingleDocTypeConformanceTest : public ConformanceTest
{
protected:
    SingleDocTypeConformanceTest();
};

}
