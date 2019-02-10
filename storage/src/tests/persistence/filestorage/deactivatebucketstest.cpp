// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/persistence/spi/test.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>

using storage::spi::test::makeSpiBucket;

namespace storage {

class DeactivateBucketsTest : public FileStorTestFixture
{
    bool isActive(const document::BucketId&) const;
public:
    void bucketsInDatabaseDeactivatedWhenNodeDownInClusterState(); 

    CPPUNIT_TEST_SUITE(DeactivateBucketsTest);
    CPPUNIT_TEST(bucketsInDatabaseDeactivatedWhenNodeDownInClusterState);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(DeactivateBucketsTest);

bool
DeactivateBucketsTest::isActive(const document::BucketId& bucket) const
{
    StorBucketDatabase::WrappedEntry entry(
            _node->getStorageBucketDatabase().get(bucket, "foo"));
    CPPUNIT_ASSERT(entry.exist());
    return entry->info.isActive();
}

void
DeactivateBucketsTest::bucketsInDatabaseDeactivatedWhenNodeDownInClusterState()
{
    TestFileStorComponents c(*this, "bucketsInDatabaseDeactivatedWhenNodeDownInClusterState");
    // Must set state to up first, or down-edge case won't trigger.
    std::string upState("storage:2 distributor:2");
    _node->getStateUpdater().setClusterState(
            lib::ClusterState::CSP(new lib::ClusterState(upState)));

    document::BucketId bucket(8, 123);

    createBucket(bucket);
    api::BucketInfo serviceLayerInfo(1, 2, 3, 4, 5, true, true);
    {
        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(bucket, "foo",
                        StorBucketDatabase::CREATE_IF_NONEXISTING));
        entry->disk = 0;
        entry->info = serviceLayerInfo;
        entry.write();
    }
    CPPUNIT_ASSERT(isActive(bucket));
    std::string downState("storage:2 .1.s:d distributor:2");
    _node->getStateUpdater().setClusterState(
            lib::ClusterState::CSP(new lib::ClusterState(downState)));

    // Buckets should have been deactivated in content layer
    CPPUNIT_ASSERT(!isActive(bucket));
}

} // namespace storage
