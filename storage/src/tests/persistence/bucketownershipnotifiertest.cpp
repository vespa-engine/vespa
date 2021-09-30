// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/common/message_sender_stub.h>
#include <tests/common/teststorageapp.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/persistence/bucketownershipnotifier.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage {

struct BucketOwnershipNotifierTest : public Test {
    std::unique_ptr<TestServiceLayerApp> _app;
    lib::ClusterState _clusterState;

    BucketOwnershipNotifierTest()
        : _app(),
          _clusterState("distributor:2 storage:1")
    {}

    void SetUp() override;

    bool ownsBucket(uint16_t distributorIndex,
                    const document::BucketId& bucket) const
    {
        uint16_t distributor = _app->getDistribution()->getIdealDistributorNode(
                _clusterState, bucket);
        return distributor == distributorIndex;
    }
    
    document::Bucket getFirstNonOwnedBucket() {
        for (int i = 0; i < 1000; ++i) {
            if (!ownsBucket(0, document::BucketId(16, i))) {
                return makeDocumentBucket(document::BucketId(16, i));
            }
        }
        return makeDocumentBucket(document::BucketId(0));
    }
        
    document::Bucket getFirstOwnedBucket() {
        for (int i = 0; i < 1000; ++i) {
            if (ownsBucket(0, document::BucketId(16, i))) {
                return makeDocumentBucket(document::BucketId(16, i));
            }
        }
        return makeDocumentBucket(document::BucketId(0));
    }

    void doTestNotification(const document::Bucket &bucket,
                            const api::BucketInfo& info,
                            const std::string& wantedSend);
};

void
BucketOwnershipNotifierTest::SetUp()
{
    _app = std::make_unique<TestServiceLayerApp>();
    _app->setDistribution(Redundancy(1), NodeCount(2));
    _app->setClusterState(_clusterState);
}

void
BucketOwnershipNotifierTest::doTestNotification(const document::Bucket &bucket,
                                                const api::BucketInfo& info,
                                                const std::string& wantedSend)
{
    ServiceLayerComponent component(_app->getComponentRegister(), "dummy");
    MessageSenderStub sender;

    BucketOwnershipNotifier notifier(component, sender);

    notifier.notifyIfOwnershipChanged(bucket, 0, info);

    EXPECT_EQ(wantedSend, sender.getCommands(true, true));
}

TEST_F(BucketOwnershipNotifierTest, send_notify_bucket_change_if_owning_distributor_changed) {
    api::BucketInfo info(0x1, 2, 3);
    document::Bucket bucket(getFirstNonOwnedBucket());
    ASSERT_NE(bucket.getBucketId().getRawId(), 0ULL);

    std::ostringstream wanted;
    wanted << "NotifyBucketChangeCommand("
           << bucket.getBucketId()
           << ", " << info
           << ") => 1";

    doTestNotification(bucket, info, wanted.str());
}

TEST_F(BucketOwnershipNotifierTest, do_not_send_notify_bucket_change_if_bucket_owned_by_initial_sender) {
    api::BucketInfo info(0x1, 2, 3);
    document::Bucket bucket(getFirstOwnedBucket());
    ASSERT_NE(bucket.getBucketId().getRawId(), 0ULL);

    doTestNotification(bucket, info, "");
}

TEST_F(BucketOwnershipNotifierTest, ignore_ideal_state_calculation_exceptions) {
    api::BucketInfo info(0x1, 2, 3);
    document::Bucket bucket(getFirstNonOwnedBucket());
    ASSERT_NE(bucket.getBucketId().getRawId(), 0ULL);

    _app->setClusterState(lib::ClusterState("distributor:0 storage:1"));

    doTestNotification(bucket, info, "");
}

TEST_F(BucketOwnershipNotifierTest, guard_notify_always) {
    ServiceLayerComponent component(_app->getComponentRegister(), "dummy");
    MessageSenderStub sender;
    BucketOwnershipNotifier notifier(component, sender);
    std::ostringstream wanted;
    {
        NotificationGuard guard(notifier);

        api::BucketInfo info(0x1, 2, 3);
        document::Bucket bucket1(getFirstOwnedBucket());
        guard.notifyAlways(bucket1, info);

        document::Bucket bucket2(getFirstNonOwnedBucket());
        guard.notifyAlways(bucket2, info);

        wanted << "NotifyBucketChangeCommand("
               << bucket1.getBucketId()
               << ", " << info
               << ") => 0,"
               << "NotifyBucketChangeCommand("
               << bucket2.getBucketId()
               << ", " << info
               << ") => 1";
    }

    EXPECT_EQ(wanted.str(), sender.getCommands(true, true));
}

} // storage

