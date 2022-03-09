// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/common/testhelper.h>
#include <tests/common/dummystoragelink.h>
#include <tests/common/teststorageapp.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/storage/persistence/filestorage/modifiedbucketchecker.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace ::testing;

namespace storage {

struct ModifiedBucketCheckerTest : Test {
    enum {
        MESSAGE_WAIT_TIME = 60*2
    };

    void SetUp() override;
    void TearDown() override;

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

void
ModifiedBucketCheckerTest::SetUp()
{
    _config.reset(new vdstestlib::DirConfig(getStandardConfig(true)));
    _node.reset(new TestServiceLayerApp(NodeIndex(0), _config->getConfigId()));
    _node->setupDummyPersistence();

    _top.reset(new DummyStorageLink);
    _handler = new ModifiedBucketChecker(_node->getComponentRegister(),
                                         _node->getPersistenceProvider(),
                                         config::ConfigUri(_config->getConfigId()));
    _top->push_back(std::unique_ptr<StorageLink>(_handler));
    _bottom = new DummyStorageLink;
    _handler->push_back(std::unique_ptr<StorageLink>(_bottom));
}

void
ModifiedBucketCheckerTest::TearDown()
{
    _top->close();
    _top.reset();
    _node.reset();
    _config.reset();
}

void
ModifiedBucketCheckerTest::modifyBuckets(uint32_t count, uint32_t firstBucket)
{
    spi::BucketIdListResult::List buckets;
    for (uint32_t i = firstBucket; i < firstBucket + count; ++i) {
        buckets.push_back(document::BucketId(16, i));
    }
    getDummyPersistence().setModifiedBuckets(std::move(buckets));
}

void
ModifiedBucketCheckerTest::replyToAll(
        const std::vector<api::StorageMessage::SP>& messages,
        uint32_t firstBucket)
{
    for (uint32_t i = 0; i < messages.size(); ++i) {
        auto& cmd = dynamic_cast<RecheckBucketInfoCommand&>(*messages[i]);
        ASSERT_EQ(document::BucketId(16, i + firstBucket), cmd.getBucketId());
        _bottom->sendUp(cmd.makeReply());
    }
}

void
ModifiedBucketCheckerTest::expectCommandsAndSendReplies(
        uint32_t count, uint32_t firstBucket)
{
    std::vector<api::StorageMessage::SP> messages(_bottom->getCommandsOnce());
    ASSERT_EQ(count, messages.size());
    replyToAll(messages, firstBucket);
}

TEST_F(ModifiedBucketCheckerTest, modified_bucket_thread_sends_recheck_bucket_commands) {
    _top->open(); // Multi-threaded test
    modifyBuckets(3, 0);
    // Should now get 3 RecheckBucketInfo commands down the dummy link.
    _bottom->waitForMessages(3, MESSAGE_WAIT_TIME);
    ASSERT_NO_FATAL_FAILURE(expectCommandsAndSendReplies(3, 0));
    // No replies should reach top link
    EXPECT_EQ(0, _top->getNumReplies());
}

TEST_F(ModifiedBucketCheckerTest, do_not_check_modified_buckets_if_already_pending) {
    _handler->setUnitTestingSingleThreadedMode();
    _top->open();
    modifyBuckets(3, 0);
    _handler->tick();

    auto messages = _bottom->getCommandsOnce();
    ASSERT_EQ(3, messages.size());

    modifyBuckets(3, 3);
    _handler->tick();
    ASSERT_NO_FATAL_FAILURE(expectCommandsAndSendReplies(0, 0));
    // After replies received, tick should send new requests again.
    ASSERT_NO_FATAL_FAILURE(replyToAll(messages, 0));
    _handler->tick(); // global bucket space ==> nothing to do
    ASSERT_NO_FATAL_FAILURE(expectCommandsAndSendReplies(0, 0));
    _handler->tick();
    ASSERT_NO_FATAL_FAILURE(expectCommandsAndSendReplies(3, 3));
}

TEST_F(ModifiedBucketCheckerTest, bucket_checker_only_swallows_recheck_bucket_replies) {
    _top->open();
    DestroyIteratorCommand cmd(spi::IteratorId(123));
    _bottom->sendUp(api::StorageMessage::SP(cmd.makeReply()));
    ASSERT_EQ(1, _top->getNumReplies());
}

TEST_F(ModifiedBucketCheckerTest, recheck_requests_are_chunked) {
    namespace cfgns = vespa::config::content::core;
    _handler->setUnitTestingSingleThreadedMode();
    _top->open();
    cfgns::StorServerConfigBuilder cfgBuilder;
    cfgBuilder.bucketRecheckingChunkSize = 2;
    _handler->configure(std::make_unique<cfgns::StorServerConfig>(cfgBuilder));

    modifyBuckets(5, 0);
    _handler->tick();

    modifyBuckets(1, 10); // should not be checked yet;
    // Rechecks should now be done in 3 chunks of 2, 2 and 1 each, respectively.
    ASSERT_NO_FATAL_FAILURE(expectCommandsAndSendReplies(2, 0));

    _handler->tick();
    ASSERT_NO_FATAL_FAILURE(expectCommandsAndSendReplies(2, 2));

    _handler->tick();
    ASSERT_NO_FATAL_FAILURE(expectCommandsAndSendReplies(1, 4));

    _handler->tick(); // global bucket space ==> nothing to do
    ASSERT_NO_FATAL_FAILURE(expectCommandsAndSendReplies(0, 0));

    // New round of fetching
    _handler->tick();
    ASSERT_NO_FATAL_FAILURE(    expectCommandsAndSendReplies(1, 10));
    _handler->tick(); // global bucket space ==> nothing to do
    ASSERT_NO_FATAL_FAILURE(expectCommandsAndSendReplies(0, 0));

    // And done!
    _handler->tick();
    ASSERT_NO_FATAL_FAILURE(expectCommandsAndSendReplies(0, 0));
    _handler->tick(); // global bucket space ==> nothing to do
    ASSERT_NO_FATAL_FAILURE(expectCommandsAndSendReplies(0, 0));
}
TEST_F(ModifiedBucketCheckerTest, invalid_chunk_size_config_is_rejected) {
    namespace cfgns = vespa::config::content::core;
    _handler->setUnitTestingSingleThreadedMode();
    _top->open();
    cfgns::StorServerConfigBuilder cfgBuilder;
    cfgBuilder.bucketRecheckingChunkSize = 0;
    EXPECT_THROW(_handler->configure(std::make_unique<cfgns::StorServerConfig>(cfgBuilder)),
                 config::InvalidConfigException);
}

// RecheckBucketInfoCommand handling is done in persistence threads,
// so that functionality is tested in the filestor tests.

} // ns storage
