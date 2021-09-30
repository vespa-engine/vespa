// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/documentid.h>
#include <vespa/persistence/spi/bucket.h>
#include <vespa/metrics/valuemetric.h>
#include <future>

namespace storage::spi { class Result; }

namespace storage {

/*
 * Result of a bucket diff entry spi operation (putAsync or removeAsync)
 */
class ApplyBucketDiffEntryResult {
    using FutureResult = std::future<std::pair<std::unique_ptr<spi::Result>, double>>;
    FutureResult         _future_result;
    spi::Bucket          _bucket;
    document::DocumentId _doc_id;
    const char*          _op;
    metrics::DoubleAverageMetric& _latency_metric;

public:
    ApplyBucketDiffEntryResult(FutureResult future_result, spi::Bucket bucket, document::DocumentId doc_id, const char *op, metrics::DoubleAverageMetric& latency_metric);
    ApplyBucketDiffEntryResult(ApplyBucketDiffEntryResult &&rhs);
    ~ApplyBucketDiffEntryResult();
    void wait();
    void check_result();
};

}
