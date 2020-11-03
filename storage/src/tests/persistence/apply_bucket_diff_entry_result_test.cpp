// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/persistence/apply_bucket_diff_entry_result.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/persistence/spi/result.h>
#include <gtest/gtest.h>

using document::DocumentId;
using document::test::makeDocumentBucket;

namespace storage {

using ResultVector = std::vector<ApplyBucketDiffEntryResult>;

namespace {

spi::Result spi_result_ok;
spi::Result spi_result_fail(spi::Result::ErrorType::RESOURCE_EXHAUSTED, "write blocked");
document::BucketIdFactory bucket_id_factory;
const char *test_op = "put";
metrics::DoubleAverageMetric dummy_metric("dummy", metrics::DoubleAverageMetric::Tags(), "dummy desc");

ApplyBucketDiffEntryResult
make_result(spi::Result &spi_result, const DocumentId &doc_id)
{
    std::promise<std::pair<std::unique_ptr<spi::Result>, double>> result_promise;
    result_promise.set_value(std::make_pair(std::make_unique<spi::Result>(spi_result), 0.1));
    spi::Bucket bucket(makeDocumentBucket(bucket_id_factory.getBucketId(doc_id)));
    return ApplyBucketDiffEntryResult(result_promise.get_future(), bucket, doc_id, test_op, dummy_metric);
}

void
check_results(ResultVector results)
{
    for (auto& result : results) {
        result.wait();
    }
    for (auto& result : results) {
        result.check_result();
    }
}

}

TEST(ApplyBucketDiffEntryResultTest, ok_results_can_be_checked)
{
    ResultVector results;
    results.push_back(make_result(spi_result_ok, DocumentId("id::test::0")));
    results.push_back(make_result(spi_result_ok, DocumentId("id::test::1")));
    check_results(std::move(results));
}

TEST(ApplyBucketDiffEntryResultTest, first_failed_result_throws_exception)
{
    ResultVector results;
    results.push_back(make_result(spi_result_ok, DocumentId("id::test::0")));
    results.push_back(make_result(spi_result_fail, DocumentId("id::test::1")));
    results.push_back(make_result(spi_result_fail, DocumentId("id::test::2")));
    try {
        check_results(std::move(results));
        FAIL() << "Failed to throw exception for failed result";
    } catch (std::exception &e) {
        EXPECT_EQ("Failed put for id::test::1 in Bucket(0xeb4700c03842cac4): Result(5, write blocked)", std::string(e.what()));
    }
}

}
