// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "apply_bucket_diff_entry_result.h"
#include <vespa/persistence/spi/result.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>

namespace storage {

ApplyBucketDiffEntryResult::ApplyBucketDiffEntryResult(FutureResult future_result, spi::Bucket bucket, document::DocumentId doc_id, const char *op, metrics::DoubleAverageMetric& latency_metric)
    : _future_result(std::move(future_result)),
      _bucket(bucket),
      _doc_id(std::move(doc_id)),
      _op(op),
      _latency_metric(latency_metric)
{
}

ApplyBucketDiffEntryResult::ApplyBucketDiffEntryResult(ApplyBucketDiffEntryResult &&rhs) = default;

ApplyBucketDiffEntryResult::~ApplyBucketDiffEntryResult() = default;

void
ApplyBucketDiffEntryResult::wait()
{
    assert(_future_result.valid());
    _future_result.wait();
}

void
ApplyBucketDiffEntryResult::check_result()
{
    assert(_future_result.valid());
    auto result = _future_result.get();
    if (result.first->hasError()) {
        vespalib::asciistream ss;
        ss << "Failed " << _op
           << " for " << _doc_id.toString()
           << " in " << _bucket
           << ": " << result.first->toString();
        throw std::runtime_error(ss.str());
    }
    _latency_metric.addValue(result.second);
}

}
