// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/persistence/apply_bucket_diff_state.h>
#include <vespa/storage/persistence/merge_bucket_info_syncer.h>
#include <vespa/storage/persistence/filestorage/merge_handler_metrics.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/metrics/metricset.h>
#include <vespa/persistence/spi/result.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <tests/common/message_sender_stub.h>
#include <tests/persistence/persistencetestutils.h>
#include <gtest/gtest.h>

using document::DocumentId;
using document::test::makeDocumentBucket;
using vespalib::MonitoredRefCount;
using vespalib::RetainGuard;

namespace storage {

namespace {

spi::Result spi_result_ok;
spi::Result spi_result_fail(spi::Result::ErrorType::RESOURCE_EXHAUSTED, "write blocked");
document::BucketIdFactory bucket_id_factory;
const char *test_op = "put";
document::Bucket dummy_document_bucket(makeDocumentBucket(document::BucketId(0, 16)));

class DummyMergeBucketInfoSyncer : public MergeBucketInfoSyncer
{
    uint32_t& _sync_count;
    vespalib::string _fail;
public:
    DummyMergeBucketInfoSyncer(uint32_t& sync_count)
        : MergeBucketInfoSyncer(),
          _sync_count(sync_count),
          _fail()
    {
    }
    ~DummyMergeBucketInfoSyncer();
    void sync_bucket_info(const spi::Bucket& bucket) const override {
        EXPECT_EQ(bucket, spi::Bucket(dummy_document_bucket));
        ++_sync_count;
        if (!_fail.empty()) {
            throw std::runtime_error(_fail);
        }
    }
    void schedule_delayed_delete(std::unique_ptr<ApplyBucketDiffState>) const override { }
    void set_fail(vespalib::string fail) { _fail = std::move(fail); }
};

DummyMergeBucketInfoSyncer::~DummyMergeBucketInfoSyncer() = default;

void
make_result(ApplyBucketDiffState& state, spi::Result &spi_result, const DocumentId &doc_id)
{
    state.on_entry_complete(std::make_unique<spi::Result>(spi_result), doc_id, test_op);
}

void push_ok(ApplyBucketDiffState &state)
{
    make_result(state, spi_result_ok, DocumentId("id::test::0"));
    make_result(state, spi_result_ok, DocumentId("id::test::1"));
}

void push_bad(ApplyBucketDiffState &state)
{
    make_result(state, spi_result_ok, DocumentId("id::test::0"));
    make_result(state, spi_result_fail, DocumentId("id::test::1"));
    make_result(state, spi_result_fail, DocumentId("id::test::2"));
}

}

class ApplyBucketDiffStateTestBase : public PersistenceTestUtils
{
public:
    uint32_t                   sync_count;
    DummyMergeBucketInfoSyncer syncer;
    metrics::MetricSet         merge_handler_metrics_owner;
    MergeHandlerMetrics        merge_handler_metrics;
    FileStorThreadMetrics::Op  op_metrics;
    framework::defaultimplementation::FakeClock clock;
    MessageSenderStub          message_sender;
    MonitoredRefCount          monitored_ref_count;

    ApplyBucketDiffStateTestBase()
        : PersistenceTestUtils(),
          sync_count(0u),
          syncer(sync_count),
          merge_handler_metrics_owner("owner", {}, "owner"),
          merge_handler_metrics(&merge_handler_metrics_owner),
          op_metrics("op", "op", &merge_handler_metrics_owner),
          clock(),
          monitored_ref_count()
    {
    }

    ~ApplyBucketDiffStateTestBase();

    std::shared_ptr<ApplyBucketDiffState> make_state() {
        return ApplyBucketDiffState::create(syncer, merge_handler_metrics, clock, spi::Bucket(dummy_document_bucket), RetainGuard(monitored_ref_count));
    }

    MessageTracker::UP
    create_tracker(std::shared_ptr<api::StorageMessage> cmd, document::Bucket bucket) {
        return MessageTracker::createForTesting(framework::MilliSecTimer(clock), getEnv(),
                                                message_sender, NoBucketLock::make(bucket), std::move(cmd));
    }

};

ApplyBucketDiffStateTestBase::~ApplyBucketDiffStateTestBase() = default;

class ApplyBucketDiffStateTest : public ApplyBucketDiffStateTestBase
{
public:
    std::shared_ptr<ApplyBucketDiffState> state;

    ApplyBucketDiffStateTest()
        : ApplyBucketDiffStateTestBase(),
          state(make_state())
    {
    }

    void reset() {
        state = make_state();
    }

    void check_failure(std::string expected) {
        auto future = state->get_future();
        state.reset();
        std::string fail_message = future.get();
        EXPECT_EQ(expected, fail_message);
    }
    void check_failure() {
        check_failure("Failed put for id::test::1 in Bucket(0x0000000000000010): Result(5, write blocked)");
    }

    void test_delayed_reply(bool failed, bool async_failed, bool chained_reply);

};

void
ApplyBucketDiffStateTest::test_delayed_reply(bool failed, bool async_failed, bool chained_reply)
{
    auto cmd = std::make_shared<api::MergeBucketCommand>(dummy_document_bucket, std::vector<api::MergeBucketCommand::Node>{}, 0);
    std::shared_ptr<api::StorageReply> reply = cmd->makeReply();
    auto tracker = create_tracker(cmd, dummy_document_bucket);
    if (failed) {
        reply->setResult(api::ReturnCode::Result::INTERNAL_FAILURE);
    }
    tracker->setMetric(op_metrics);
    tracker->setReply(reply);
    if (chained_reply) {
        state->set_delayed_reply(std::move(tracker), message_sender, &op_metrics, framework::MilliSecTimer(clock), std::move(reply));
    } else {
        state->set_delayed_reply(std::move(tracker), std::move(reply));
    }
    clock.addMilliSecondsToTime(16);
    if (async_failed) {
        push_bad(*state);
    }
    state.reset();
    if (failed || async_failed) {
        EXPECT_EQ(0.0, op_metrics.latency.getLast());
        EXPECT_EQ(0, op_metrics.latency.getCount());
        EXPECT_EQ(1, op_metrics.failed.getValue());
    } else {
        EXPECT_EQ(16.0, op_metrics.latency.getLast());
        EXPECT_EQ(1, op_metrics.latency.getCount());
        EXPECT_EQ(0, op_metrics.failed.getValue());
    }
    ASSERT_EQ(1, message_sender.replies.size());
    EXPECT_NE(failed || async_failed, std::dynamic_pointer_cast<api::MergeBucketReply>(message_sender.replies.front())->getResult().success());
}

TEST_F(ApplyBucketDiffStateTest, ok_results_can_be_checked)
{
    push_ok(*state);
    check_failure("");
}

TEST_F(ApplyBucketDiffStateTest, failed_result_errors_ignored)
{
    push_bad(*state);
}

TEST_F(ApplyBucketDiffStateTest, first_failed_result_throws_exception)
{
    push_bad(*state);
    ASSERT_NO_FATAL_FAILURE(check_failure());
}

TEST_F(ApplyBucketDiffStateTest, sync_bucket_info_if_needed_on_destruct)
{
    reset();
    EXPECT_EQ(0, sync_count);
    state->mark_stale_bucket_info();
    EXPECT_EQ(0, sync_count);
    reset();
    EXPECT_EQ(1, sync_count);
}

TEST_F(ApplyBucketDiffStateTest, explicit_sync_bucket_info_works)
{
    state->sync_bucket_info();
    EXPECT_EQ(0, sync_count);
    state->mark_stale_bucket_info();
    state->sync_bucket_info();
    EXPECT_EQ(1, sync_count);
    state->sync_bucket_info();
    EXPECT_EQ(1, sync_count);
    reset();
    EXPECT_EQ(1, sync_count);
}

TEST_F(ApplyBucketDiffStateTest, failed_sync_bucket_info_is_detected)
{
    vespalib::string fail("sync bucket failed");
    syncer.set_fail(fail);
    state->mark_stale_bucket_info();
    check_failure(fail);
}

TEST_F(ApplyBucketDiffStateTest, data_write_latency_is_updated)
{
    clock.addMilliSecondsToTime(10);
    state.reset();
    EXPECT_EQ(10.0, merge_handler_metrics.mergeDataWriteLatency.getLast());
    EXPECT_EQ(1, merge_handler_metrics.mergeDataWriteLatency.getCount());
}

TEST_F(ApplyBucketDiffStateTest, total_latency_is_not_updated)
{
    clock.addMilliSecondsToTime(14);
    state.reset();
    EXPECT_EQ(0.0, merge_handler_metrics.mergeLatencyTotal.getLast());
    EXPECT_EQ(0, merge_handler_metrics.mergeLatencyTotal.getCount());
}

TEST_F(ApplyBucketDiffStateTest, total_latency_is_updated)
{
    state->set_merge_start_time(framework::MilliSecTimer(clock));
    clock.addMilliSecondsToTime(14);
    state.reset();
    EXPECT_EQ(14.0, merge_handler_metrics.mergeLatencyTotal.getLast());
    EXPECT_EQ(1, merge_handler_metrics.mergeLatencyTotal.getCount());
}

TEST_F(ApplyBucketDiffStateTest, delayed_ok_reply)
{
    test_delayed_reply(false, false, false);
}

TEST_F(ApplyBucketDiffStateTest, delayed_failed_reply)
{
    test_delayed_reply(true, false, false);
}

TEST_F(ApplyBucketDiffStateTest, delayed_ok_chained_reply)
{
    test_delayed_reply(false, false, true);
}

TEST_F(ApplyBucketDiffStateTest, delayed_failed_chained_reply)
{
    test_delayed_reply(true, false, true);
}

TEST_F(ApplyBucketDiffStateTest, delayed_async_failed_reply)
{
    test_delayed_reply(false, true, false);
}

}
