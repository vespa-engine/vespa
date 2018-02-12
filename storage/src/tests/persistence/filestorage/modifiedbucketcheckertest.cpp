// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <tests/common/testhelper.h>
#include <tests/common/storagelinktest.h>
#include <tests/common/teststorageapp.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/storage/persistence/filestorage/modifiedbucketchecker.h>
#include <vespa/config/common/exceptions.h>

namespace storage {

class ModifiedBucketCheckerTest : public CppUnit::TestFixture
{
public:
    enum {
        MESSAGE_WAIT_TIME = 60*2
    };

    void setUp() override;
    void tearDown() override;

    void testModifiedBucketThreadSendsRecheckBucketCommands();
    void testDoNotCheckModifiedBucketsIfAlreadyPending();
    void testBucketCheckerOnlySwallowsRecheckBucketReplies();
    void testRecheckRequestsAreChunked();
    void testInvalidChunkSizeConfigIsRejected();

    CPPUNIT_TEST_SUITE(ModifiedBucketCheckerTest);
    CPPUNIT_TEST(testModifiedBucketThreadSendsRecheckBucketCommands);
    CPPUNIT_TEST(testDoNotCheckModifiedBucketsIfAlreadyPending);
    CPPUNIT_TEST(testBucketCheckerOnlySwallowsRecheckBucketReplies);
    CPPUNIT_TEST(testRecheckRequestsAreChunked);
    CPPUNIT_TEST(testInvalidChunkSizeConfigIsRejected);
    CPPUNIT_TEST_SUITE_END();
private:
    spi::dummy::DummyPersistence& getDummyPersistence() {
        return static_cast<spi::dummy::DummyPersistence&>(
                _node->getPersistenceProvider());
    }
    void expectCommandsAndSendReplies(uint32_t count, uint32_t firstBucket);
    void modifyBuckets(uint32_t count, uint32_t firstBucket);
    void replyToAll(const std::vector<api::StorageMessage::SP>& messages,
                    uint32_t firstBucket);

    std::unique_ptr<DummyStorageLink> _top;
    ModifiedBucketChecker* _handler;
    DummyStorageLink* _bottom;

    std::unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<vdstestlib::DirConfig> _config;
};

CPPUNIT_TEST_SUITE_REGISTRATION(ModifiedBucketCheckerTest);

void
ModifiedBucketCheckerTest::setUp()
{
    _config.reset(new vdstestlib::DirConfig(getStandardConfig(true)));
    _node.reset(new TestServiceLayerApp(DiskCount(1), NodeIndex(0),
                                        _config->getConfigId()));
    _node->setupDummyPersistence();

    _top.reset(new DummyStorageLink);
    _handler = new ModifiedBucketChecker(_node->getComponentRegister(),
                                         _node->getPersistenceProvider(),
                                         _config->getConfigId());
    _top->push_back(std::unique_ptr<StorageLink>(_handler));
    _bottom = new DummyStorageLink;
    _handler->push_back(std::unique_ptr<StorageLink>(_bottom));
}

void
ModifiedBucketCheckerTest::tearDown()
{
    _top->close();
    _top.reset(0);
    _node.reset(0);
    _config.reset(0);
}

void
ModifiedBucketCheckerTest::modifyBuckets(uint32_t count, uint32_t firstBucket)
{
    spi::BucketIdListResult::List buckets;
    for (uint32_t i = firstBucket; i < firstBucket + count; ++i) {
        buckets.push_back(document::BucketId(16, i));
    }
    getDummyPersistence().setModifiedBuckets(buckets);
}

void
ModifiedBucketCheckerTest::replyToAll(
        const std::vector<api::StorageMessage::SP>& messages,
        uint32_t firstBucket)
{
    for (uint32_t i = 0; i < messages.size(); ++i) {
        RecheckBucketInfoCommand& cmd(
                dynamic_cast<RecheckBucketInfoCommand&>(*messages[i]));
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, i+firstBucket),
                             cmd.getBucketId());
        _bottom->sendUp(cmd.makeReply());
    }
}

void
ModifiedBucketCheckerTest::expectCommandsAndSendReplies(
        uint32_t count, uint32_t firstBucket)
{
    std::vector<api::StorageMessage::SP> messages(_bottom->getCommandsOnce());
    CPPUNIT_ASSERT_EQUAL(size_t(count), messages.size());
    replyToAll(messages, firstBucket);
}

void
ModifiedBucketCheckerTest::testModifiedBucketThreadSendsRecheckBucketCommands()
{
    _top->open(); // Multi-threaded test
    modifyBuckets(3, 0);
    // Should now get 3 RecheckBucketInfo commands down the dummy link.
    _bottom->waitForMessages(3, MESSAGE_WAIT_TIME);
    expectCommandsAndSendReplies(3, 0);
    // No replies should reach top link
    CPPUNIT_ASSERT_EQUAL(size_t(0), _top->getNumReplies());
}

void
ModifiedBucketCheckerTest::testDoNotCheckModifiedBucketsIfAlreadyPending()
{
    _handler->setUnitTestingSingleThreadedMode();
    _top->open();
    modifyBuckets(3, 0);
    _handler->tick();

    std::vector<api::StorageMessage::SP> messages(_bottom->getCommandsOnce());
    CPPUNIT_ASSERT_EQUAL(size_t(3), messages.size());

    modifyBuckets(3, 3);
    _handler->tick();
    expectCommandsAndSendReplies(0, 0);
    // After replies received, tick should send new requests again.
    replyToAll(messages, 0);
    _handler->tick(); // global bucket space ==> nothing to do
    expectCommandsAndSendReplies(0, 0);
    _handler->tick();
    expectCommandsAndSendReplies(3, 3);
}

void
ModifiedBucketCheckerTest::testBucketCheckerOnlySwallowsRecheckBucketReplies()
{
    _top->open();
    DestroyIteratorCommand cmd(spi::IteratorId(123));
    _bottom->sendUp(api::StorageMessage::SP(cmd.makeReply()));
    CPPUNIT_ASSERT_EQUAL(size_t(1), _top->getNumReplies());
}

void
ModifiedBucketCheckerTest::testRecheckRequestsAreChunked()
{
    namespace cfgns = vespa::config::content::core;
    _handler->setUnitTestingSingleThreadedMode();
    _top->open();
    cfgns::StorServerConfigBuilder cfgBuilder;
    cfgBuilder.bucketRecheckingChunkSize = 2;
    _handler->configure(std::unique_ptr<cfgns::StorServerConfig>(
            new cfgns::StorServerConfig(cfgBuilder)));

    modifyBuckets(5, 0);
    _handler->tick();

    modifyBuckets(1, 10); // should not be checked yet;
    // Rechecks should now be done in 3 chunks of 2, 2 and 1 each, respectively.
    expectCommandsAndSendReplies(2, 0);

    _handler->tick();
    expectCommandsAndSendReplies(2, 2);

    _handler->tick();
    expectCommandsAndSendReplies(1, 4);

    _handler->tick(); // global bucket space ==> nothing to do
    expectCommandsAndSendReplies(0, 0);

    // New round of fetching
    _handler->tick();
    expectCommandsAndSendReplies(1, 10);
    _handler->tick(); // global bucket space ==> nothing to do
    expectCommandsAndSendReplies(0, 0);

    // And done!
    _handler->tick();
    expectCommandsAndSendReplies(0, 0);
    _handler->tick(); // global bucket space ==> nothing to do
    expectCommandsAndSendReplies(0, 0);
}

void
ModifiedBucketCheckerTest::testInvalidChunkSizeConfigIsRejected()
{
    namespace cfgns = vespa::config::content::core;
    _handler->setUnitTestingSingleThreadedMode();
    _top->open();
    cfgns::StorServerConfigBuilder cfgBuilder;
    cfgBuilder.bucketRecheckingChunkSize = 0;
    try {
        _handler->configure(std::unique_ptr<cfgns::StorServerConfig>(
                new cfgns::StorServerConfig(cfgBuilder)));
        CPPUNIT_FAIL("Expected bad config to be rejected");
    } catch (const config::InvalidConfigException&) {
        // Happy days
    } catch (...) {
        CPPUNIT_FAIL("Got unexpected exception");
    }
}

// RecheckBucketInfoCommand handling is done in persistence threads,
// so that functionality is tested in the filestor tests.

} // ns storage

