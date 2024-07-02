// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/common/dummystoragelink.h>
#include <tests/common/testhelper.h>
#include <tests/persistence/common/filestortestfixture.h>
#include <tests/persistence/filestorage/forwardingmessagesender.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/storage/persistence/filestorage/filestorhandlerimpl.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storage/persistence/filestorage/filestormetrics.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <gtest/gtest.h>

using document::test::makeDocumentBucket;
using document::BucketId;
using document::DocumentId;
using namespace ::testing;

namespace storage {

struct FeedOperationBatchingTest : FileStorTestFixture {
    DummyStorageLink                         _top;
    std::unique_ptr<ForwardingMessageSender> _message_sender;
    FileStorMetrics                          _metrics;
    std::unique_ptr<FileStorHandler>         _handler;
    api::Timestamp                           _next_timestamp;

    FeedOperationBatchingTest();
    ~FeedOperationBatchingTest() override;

    void SetUp() override {
        FileStorTestFixture::SetUp();
        // This silly little indirection is a work-around for the top-level link needing something
        // below it to send _up_ into it, rather than directly receiving the messages itself.
        auto message_receiver = std::make_unique<DummyStorageLink>();
        _message_sender = std::make_unique<ForwardingMessageSender>(*message_receiver);
        _top.push_back(std::move(message_receiver));
        _top.open();
        _metrics.initDiskMetrics(1, 1);
        // By default, sets up 1 thread with 1 stripe
        _handler = std::make_unique<FileStorHandlerImpl>(*_message_sender, _metrics, _node->getComponentRegister());
        _handler->set_max_feed_op_batch_size(3);
    }

    void TearDown() override {
        _handler.reset();
        FileStorTestFixture::TearDown();
    }

    [[nodiscard]] static vespalib::string id_str_of(uint32_t bucket_idx, uint32_t doc_idx) {
        return vespalib::make_string("id:foo:testdoctype1:n=%u:%u", bucket_idx, doc_idx);
    }

    [[nodiscard]] static DocumentId id_of(uint32_t bucket_idx, uint32_t doc_idx) {
        return DocumentId(id_str_of(bucket_idx, doc_idx));
    }

    void schedule_msg(const std::shared_ptr<api::StorageMessage>& msg) {
        msg->setAddress(makeSelfAddress());
        _handler->schedule(msg); // takes shared_ptr by const ref, no point in moving
    }

    void send_put(uint32_t bucket_idx, uint32_t doc_idx, uint32_t timestamp, vespalib::duration timeout) {
        auto id = id_str_of(bucket_idx, doc_idx);
        auto doc = _node->getTestDocMan().createDocument("foobar", id);
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket({16, bucket_idx}), std::move(doc), timestamp);
        cmd->setTimeout(timeout);
        schedule_msg(cmd);
    }

    void send_put(uint32_t bucket_idx, uint32_t doc_idx) {
        send_put(bucket_idx, doc_idx, next_timestamp(), 60s);
    }

    void send_puts(std::initializer_list<std::pair<uint32_t, uint32_t>> bucket_docs) {
        for (const auto& bd : bucket_docs) {
            send_put(bd.first, bd.second);
        }
    }

    void send_get(uint32_t bucket_idx, uint32_t doc_idx) {
        auto id = id_of(bucket_idx, doc_idx);
        auto cmd = std::make_shared<api::GetCommand>(makeDocumentBucket({16, bucket_idx}), id, document::AllFields::NAME);
        schedule_msg(cmd);
    }

    void send_remove(uint32_t bucket_idx, uint32_t doc_idx, uint32_t timestamp) {
        auto id = id_of(bucket_idx, doc_idx);
        auto cmd = std::make_shared<api::RemoveCommand>(makeDocumentBucket({16, bucket_idx}), id, timestamp);
        schedule_msg(cmd);
    }

    void send_remove(uint32_t bucket_idx, uint32_t doc_idx) {
        send_remove(bucket_idx, doc_idx, next_timestamp());
    }

    void send_update(uint32_t bucket_idx, uint32_t doc_idx, uint32_t timestamp) {
        auto id = id_of(bucket_idx, doc_idx);
        auto update = std::make_shared<document::DocumentUpdate>(
                _node->getTestDocMan().getTypeRepo(),
                _node->getTestDocMan().createRandomDocument()->getType(), id);
        auto cmd = std::make_shared<api::UpdateCommand>(makeDocumentBucket({16, bucket_idx}), std::move(update), timestamp);
        schedule_msg(cmd);
    }

    void send_update(uint32_t bucket_idx, uint32_t doc_idx) {
        send_update(bucket_idx, doc_idx, next_timestamp());
    }

    [[nodiscard]] api::Timestamp next_timestamp() {
        auto ret = _next_timestamp;
        ++_next_timestamp;
        return ret;
    }

    [[nodiscard]] vespalib::steady_time fake_now() const {
        return _node->getClock().getMonotonicTime();
    }

    [[nodiscard]] vespalib::steady_time fake_deadline() const {
        return _node->getClock().getMonotonicTime() + 60s;
    }

    [[nodiscard]] FileStorHandler::LockedMessageBatch next_batch() {
        return _handler->next_message_batch(0, fake_now(), fake_deadline());
    }

    template <typename CmdType>
    static void assert_batch_msg_is(const FileStorHandler::LockedMessageBatch& batch, uint32_t msg_idx,
                                    uint32_t expected_bucket_idx, uint32_t expected_doc_idx)
    {
        ASSERT_LT(msg_idx, batch.size());
        auto msg = batch.messages[msg_idx].first;
        auto* as_cmd = dynamic_cast<const CmdType*>(msg.get());
        ASSERT_TRUE(as_cmd) << msg->toString() << " does not have the expected type";
        EXPECT_EQ(as_cmd->getBucketId(), BucketId(16, expected_bucket_idx));

        auto id = as_cmd->getDocumentId();
        ASSERT_TRUE(id.getScheme().hasNumber());
        EXPECT_EQ(id.getScheme().getNumber(), expected_bucket_idx) << id;
        std::string actual_id_part(id.getScheme().getNamespaceSpecific());
        std::string expected_id_part = std::to_string(expected_doc_idx);
        EXPECT_EQ(actual_id_part, expected_id_part) << id;
    }

    static void assert_batch_msg_is_put(const FileStorHandler::LockedMessageBatch& batch, uint32_t msg_idx,
                                        uint32_t expected_bucket_idx, uint32_t expected_doc_idx)
    {
        assert_batch_msg_is<api::PutCommand>(batch, msg_idx, expected_bucket_idx, expected_doc_idx);
    }

    static void assert_batch_msg_is_remove(const FileStorHandler::LockedMessageBatch& batch, uint32_t msg_idx,
                                           uint32_t expected_bucket_idx, uint32_t expected_doc_idx)
    {
        assert_batch_msg_is<api::RemoveCommand>(batch, msg_idx, expected_bucket_idx, expected_doc_idx);
    }

    static void assert_batch_msg_is_update(const FileStorHandler::LockedMessageBatch& batch, uint32_t msg_idx,
                                           uint32_t expected_bucket_idx, uint32_t expected_doc_idx)
    {
        assert_batch_msg_is<api::UpdateCommand>(batch, msg_idx, expected_bucket_idx, expected_doc_idx);
    }

    static void assert_batch_msg_is_get(const FileStorHandler::LockedMessageBatch& batch, uint32_t msg_idx,
                                        uint32_t expected_bucket_idx, uint32_t expected_doc_idx)
    {
        assert_batch_msg_is<api::GetCommand>(batch, msg_idx, expected_bucket_idx, expected_doc_idx);
    }

    enum Type {
        Put,
        Update,
        Remove,
        Get
    };

    static void assert_empty_batch(const FileStorHandler::LockedMessageBatch& batch) {
        ASSERT_TRUE(batch.empty());
        ASSERT_FALSE(batch.lock);
    }

    static void assert_batch(const FileStorHandler::LockedMessageBatch& batch,
                             uint32_t expected_bucket_idx,
                             std::initializer_list<std::pair<Type, uint32_t>> expected_msgs)
    {
        ASSERT_TRUE(batch.lock);
        ASSERT_EQ(batch.lock->getBucket().getBucketId(), BucketId(16, expected_bucket_idx));
        ASSERT_EQ(batch.size(), expected_msgs.size());

        uint32_t idx = 0;
        for (const auto& msg : expected_msgs) {
            switch (msg.first) {
            case Type::Put:    assert_batch_msg_is_put(batch,    idx, expected_bucket_idx, msg.second); break;
            case Type::Update: assert_batch_msg_is_update(batch, idx, expected_bucket_idx, msg.second); break;
            case Type::Remove: assert_batch_msg_is_remove(batch, idx, expected_bucket_idx, msg.second); break;
            case Type::Get:    assert_batch_msg_is_get(batch,    idx, expected_bucket_idx, msg.second); break;
            default: FAIL();
            }
            ++idx;
        }
    }
};

FeedOperationBatchingTest::FeedOperationBatchingTest()
    : FileStorTestFixture(),
      _top(),
      _message_sender(),
      _metrics(),
      _handler(),
      _next_timestamp(1000)
{
}

FeedOperationBatchingTest::~FeedOperationBatchingTest() = default;

// Note: unless explicitly set by the testcase, max batch size is 3

TEST_F(FeedOperationBatchingTest, batching_is_disabled_with_1_max_batch_size) {
    _handler->set_max_feed_op_batch_size(1);
    send_puts({{1, 1}, {1, 2}, {2, 3}, {2, 4}});
    // No batching; has the same behavior as current FIFO
    assert_batch(next_batch(), 1, {{Put, 1}});
    assert_batch(next_batch(), 1, {{Put, 2}});
    assert_batch(next_batch(), 2, {{Put, 3}});
    assert_batch(next_batch(), 2, {{Put, 4}});
    assert_empty_batch(next_batch());
}

TEST_F(FeedOperationBatchingTest, batching_is_limited_to_configured_max_size) {
    send_puts({{1, 1}, {1, 2}, {1, 3}, {1, 4}, {1, 5}});
    assert_batch(next_batch(), 1, {{Put, 1}, {Put, 2}, {Put, 3}});
    assert_batch(next_batch(), 1, {{Put, 4}, {Put, 5}});
    assert_empty_batch(next_batch());
}

TEST_F(FeedOperationBatchingTest, batching_can_consume_entire_queue) {
    send_puts({{1, 1}, {1, 2}, {1, 3}});
    assert_batch(next_batch(), 1, {{Put, 1}, {Put, 2}, {Put, 3}});
    assert_empty_batch(next_batch());
}

TEST_F(FeedOperationBatchingTest, batching_is_only_done_for_single_bucket) {
    send_puts({{1, 1}, {2, 2}, {2, 3}, {2, 4}, {3, 5}});
    assert_batch(next_batch(), 1, {{Put, 1}});
    assert_batch(next_batch(), 2, {{Put, 2}, {Put, 3}, {Put, 4}});
    assert_batch(next_batch(), 3, {{Put, 5}});
}

TEST_F(FeedOperationBatchingTest, batch_can_include_all_supported_feed_op_types) {
    send_put(1, 1);
    send_remove(1, 2);
    send_update(1, 3);
    assert_batch(next_batch(), 1, {{Put, 1}, {Remove, 2}, {Update, 3}});
}

TEST_F(FeedOperationBatchingTest, timed_out_reqeusts_are_ignored_by_batch) {
    send_puts({{1, 1}});
    send_put(1, 2, next_timestamp(), 1s);
    send_puts({{1, 3}});
    _node->getClock().addSecondsToTime(2);
    // Put #2 with 1s timeout has expired in the queue and should not be returned as part of the batch
    assert_batch(next_batch(), 1, {{Put, 1}, {Put, 3}});
    ASSERT_EQ(_top.getNumReplies(), 0);
    // The actual timeout is handled by the next message fetch invocation
    assert_empty_batch(next_batch());
    ASSERT_EQ(_top.getNumReplies(), 1);
    EXPECT_EQ(dynamic_cast<api::StorageReply&>(*_top.getReply(0)).getResult().getResult(), api::ReturnCode::TIMEOUT);
}

TEST_F(FeedOperationBatchingTest, non_feed_ops_are_not_batched) {
    send_get(1, 2);
    send_get(1, 3);
    assert_batch(next_batch(), 1, {{Get, 2}});
    assert_batch(next_batch(), 1, {{Get, 3}});
}

TEST_F(FeedOperationBatchingTest, pipeline_stalled_by_non_feed_op) {
    // It can reasonably be argued that we could batch _around_ a Get operation and still
    // have correct behavior, but the Get here is just a stand-in for an arbitrary operation such
    // as a Split (which changes the bucket set), which is rather more tricky to reason about.
    // For simplicity and understandability, just stall the batch pipeline (at least for now).
    send_get(1, 2);
    send_puts({{1, 3}, {1, 4}});
    send_get(1, 5);
    send_puts({{1, 6}, {1, 7}});

    assert_batch(next_batch(), 1, {{Get, 2}}); // If first op is non-feed, only it should be returned
    assert_batch(next_batch(), 1, {{Put, 3}, {Put, 4}});
    assert_batch(next_batch(), 1, {{Get, 5}});
    assert_batch(next_batch(), 1, {{Put, 6}, {Put, 7}});
}

TEST_F(FeedOperationBatchingTest, pipeline_stalled_by_concurrent_ops_to_same_document) {
    // 2 ops to doc #2. Since this is expected to be a very rare edge case, just
    // stop batching at that point and defer the concurrent op to the next batch.
    send_puts({{1, 1}, {1, 2}, {1, 3}, {1, 2}, {1, 4}});
    assert_batch(next_batch(), 1, {{Put, 1}, {Put, 2}, {Put, 3}});
    assert_batch(next_batch(), 1, {{Put, 2}, {Put, 4}});
}

TEST_F(FeedOperationBatchingTest, batch_respects_persistence_throttling) {
    vespalib::SharedOperationThrottler::DynamicThrottleParams params;
    params.min_window_size = 3;
    params.max_window_size = 3;
    params.window_size_increment = 1;
    _handler->use_dynamic_operation_throttling(true);
    _handler->reconfigure_dynamic_throttler(params);
    _handler->set_max_feed_op_batch_size(10); // > win size to make sure we test the right thing

    send_puts({{1, 1}, {1, 2}, {1, 3}, {1, 4}, {1, 5}});
    auto batch = next_batch(); // holds 3 throttle tokens
    assert_batch(batch, 1, {{Put, 1}, {Put, 2}, {Put, 3}});
    // No more throttle tokens available
    assert_empty_batch(next_batch());
}

} // storage
