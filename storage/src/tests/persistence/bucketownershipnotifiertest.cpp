// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <tests/distributor/messagesenderstub.h>
#include <tests/common/teststorageapp.h>
#include <vespa/storage/persistence/bucketownershipnotifier.h>

namespace storage {

class BucketOwnershipNotifierTest : public CppUnit::TestFixture
{
    std::unique_ptr<TestServiceLayerApp> _app;
    lib::ClusterState _clusterState;
public:

    BucketOwnershipNotifierTest()
        : _app(),
          _clusterState("distributor:2 storage:1")
    {}

    void setUp() override;

    CPPUNIT_TEST_SUITE(BucketOwnershipNotifierTest);
    CPPUNIT_TEST(testSendNotifyBucketChangeIfOwningDistributorChanged);
    CPPUNIT_TEST(testDoNotSendNotifyBucketChangeIfBucketOwnedByInitialSender);
    CPPUNIT_TEST(testIgnoreIdealStateCalculationExceptions);
    CPPUNIT_TEST(testGuardNotifyAlways);
    CPPUNIT_TEST_SUITE_END();

    bool ownsBucket(uint16_t distributorIndex,
                    const document::BucketId& bucket) const
    {
        uint16_t distributor = _app->getDistribution()->getIdealDistributorNode(
                _clusterState, bucket);
        return distributor == distributorIndex;
    }
    
    document::BucketId getFirstNonOwnedBucket() {
        for (int i = 0; i < 1000; ++i) {
            if (!ownsBucket(0, document::BucketId(16, i))) {
                return document::BucketId(16, i);
            }
        }
        return document::BucketId(0);
    }
        
    document::BucketId getFirstOwnedBucket() {
        for (int i = 0; i < 1000; ++i) {
            if (ownsBucket(0, document::BucketId(16, i))) {
                return document::BucketId(16, i);
            }
        }
        return document::BucketId(0);
    }


    void testSendNotifyBucketChangeIfOwningDistributorChanged();
    void testDoNotSendNotifyBucketChangeIfBucketOwnedByInitialSender();
    void testIgnoreIdealStateCalculationExceptions();
    void testGuardNotifyAlways();

    void doTestNotification(const document::BucketId& bucket,
                            const api::BucketInfo& info,
                            const std::string& wantedSend);
};

CPPUNIT_TEST_SUITE_REGISTRATION(BucketOwnershipNotifierTest);

void
BucketOwnershipNotifierTest::setUp()
{
    _app.reset(new TestServiceLayerApp);
    _app->setDistribution(Redundancy(1), NodeCount(2));
    _app->setClusterState(_clusterState);
}

void
BucketOwnershipNotifierTest::doTestNotification(const document::BucketId& bucket,
                                                const api::BucketInfo& info,
                                                const std::string& wantedSend)
{
    ServiceLayerComponent component(_app->getComponentRegister(), "dummy");
    MessageSenderStub sender;

    BucketOwnershipNotifier notifier(component, sender);

    notifier.notifyIfOwnershipChanged(bucket, 0, info);

    CPPUNIT_ASSERT_EQUAL(wantedSend, sender.getCommands(true, true));
}

void
BucketOwnershipNotifierTest::testSendNotifyBucketChangeIfOwningDistributorChanged()
{
    api::BucketInfo info(0x1, 2, 3);
    document::BucketId bucket(getFirstNonOwnedBucket());
    CPPUNIT_ASSERT(bucket.getRawId() != 0);

    std::ostringstream wanted;
    wanted << "NotifyBucketChangeCommand("
           << bucket
           << ", " << info
           << ") => 1";

    doTestNotification(bucket, info, wanted.str());
}

void
BucketOwnershipNotifierTest::testDoNotSendNotifyBucketChangeIfBucketOwnedByInitialSender()
{
    api::BucketInfo info(0x1, 2, 3);
    document::BucketId bucket(getFirstOwnedBucket());
    CPPUNIT_ASSERT(bucket.getRawId() != 0);

    doTestNotification(bucket, info, "");
}

void
BucketOwnershipNotifierTest::testIgnoreIdealStateCalculationExceptions()
{
    api::BucketInfo info(0x1, 2, 3);
    document::BucketId bucket(getFirstNonOwnedBucket());
    CPPUNIT_ASSERT(bucket.getRawId() != 0);

    _app->setClusterState(lib::ClusterState("distributor:0 storage:1"));

    doTestNotification(bucket, info, "");
}

void
BucketOwnershipNotifierTest::testGuardNotifyAlways()
{
    ServiceLayerComponent component(_app->getComponentRegister(), "dummy");
    MessageSenderStub sender;
    BucketOwnershipNotifier notifier(component, sender);
    std::ostringstream wanted;
    {
        NotificationGuard guard(notifier);

        api::BucketInfo info(0x1, 2, 3);
        document::BucketId bucket1(getFirstOwnedBucket());
        guard.notifyAlways(bucket1, info);

        document::BucketId bucket2(getFirstNonOwnedBucket());
        guard.notifyAlways(bucket2, info);

        wanted << "NotifyBucketChangeCommand("
               << bucket1
               << ", " << info
               << ") => 0,"
               << "NotifyBucketChangeCommand("
               << bucket2
               << ", " << info
               << ") => 1";
    }

    CPPUNIT_ASSERT_EQUAL(wanted.str(), sender.getCommands(true, true));
}

} // storage

