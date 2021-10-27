// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/persistence/apply_bucket_diff_state.h>
#include <vespa/storage/persistence/merge_bucket_info_syncer.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/persistence/spi/result.h>
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

class ApplyBucketDiffStateTestBase : public ::testing::Test
{
public:
    uint32_t                   sync_count;
    DummyMergeBucketInfoSyncer syncer;
    MonitoredRefCount          monitored_ref_count;

    ApplyBucketDiffStateTestBase()
        : ::testing::Test(),
          sync_count(0u),
          syncer(sync_count)
    {
    }

    ~ApplyBucketDiffStateTestBase();

    std::shared_ptr<ApplyBucketDiffState> make_state() {
        return ApplyBucketDiffState::create(syncer, spi::Bucket(dummy_document_bucket), RetainGuard(monitored_ref_count));
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

};

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

}
