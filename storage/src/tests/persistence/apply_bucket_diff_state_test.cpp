// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/persistence/apply_bucket_diff_entry_result.h>
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

namespace storage {

namespace {

spi::Result spi_result_ok;
spi::Result spi_result_fail(spi::Result::ErrorType::RESOURCE_EXHAUSTED, "write blocked");
document::BucketIdFactory bucket_id_factory;
const char *test_op = "put";
metrics::DoubleAverageMetric dummy_metric("dummy", metrics::DoubleAverageMetric::Tags(), "dummy desc");
document::Bucket dummy_document_bucket(makeDocumentBucket(document::BucketId(0, 16)));

class DummyMergeBucketInfoSyncer : public MergeBucketInfoSyncer
{
    uint32_t& _sync_count;
public:
    DummyMergeBucketInfoSyncer(uint32_t& sync_count)
        : MergeBucketInfoSyncer(),
          _sync_count(sync_count)
    {
    }
    void sync_bucket_info(const spi::Bucket& bucket) const override {
        EXPECT_EQ(bucket, spi::Bucket(dummy_document_bucket));
        ++_sync_count;
    }
};

ApplyBucketDiffEntryResult
make_result(spi::Result &spi_result, const DocumentId &doc_id)
{
    std::promise<std::pair<std::unique_ptr<spi::Result>, double>> result_promise;
    result_promise.set_value(std::make_pair(std::make_unique<spi::Result>(spi_result), 0.1));
    spi::Bucket bucket(makeDocumentBucket(bucket_id_factory.getBucketId(doc_id)));
    return ApplyBucketDiffEntryResult(result_promise.get_future(), bucket, doc_id, test_op, dummy_metric);
}

void push_ok(ApplyBucketDiffState &state)
{
    state.push_back(make_result(spi_result_ok, DocumentId("id::test::0")));
    state.push_back(make_result(spi_result_ok, DocumentId("id::test::1")));
}

void push_bad(ApplyBucketDiffState &state)
{
    state.push_back(make_result(spi_result_ok, DocumentId("id::test::0")));
    state.push_back(make_result(spi_result_fail, DocumentId("id::test::1")));
    state.push_back(make_result(spi_result_fail, DocumentId("id::test::2")));
}

}

class ApplyBucketDiffStateTestBase : public ::testing::Test
{
public:
    uint32_t                   sync_count;
    DummyMergeBucketInfoSyncer syncer;

    ApplyBucketDiffStateTestBase()
        : ::testing::Test(),
          sync_count(0u),
          syncer(sync_count)
    {
    }

    std::unique_ptr<ApplyBucketDiffState> make_state() {
        return std::make_unique<ApplyBucketDiffState>(syncer, spi::Bucket(dummy_document_bucket));
    }
};

class ApplyBucketDiffStateTest : public ApplyBucketDiffStateTestBase
{
public:
    std::unique_ptr<ApplyBucketDiffState> state;

    ApplyBucketDiffStateTest()
        : ApplyBucketDiffStateTestBase(),
          state(make_state())
    {
    }

    void reset() {
        state = make_state();
    }

    void check_failure() {
        try {
            state->check();
            FAIL() << "Failed to throw exception for failed result";
        } catch (std::exception &e) {
            EXPECT_EQ("Failed put for id::test::1 in Bucket(0xeb4700c03842cac4): Result(5, write blocked)", std::string(e.what()));
        }
    }

};

TEST_F(ApplyBucketDiffStateTest, ok_results_can_be_checked)
{
    push_ok(*state);
    state->check();
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

}
