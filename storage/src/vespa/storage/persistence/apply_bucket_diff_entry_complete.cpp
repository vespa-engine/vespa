// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "apply_bucket_diff_entry_complete.h"
#include "apply_bucket_diff_state.h"
#include <vespa/persistence/spi/result.h>
#include <cassert>

namespace storage {

ApplyBucketDiffEntryComplete::ApplyBucketDiffEntryComplete(std::shared_ptr<ApplyBucketDiffState> state,
                                                           document::DocumentId doc_id,
                                                           ThrottleToken throttle_token,
                                                           const char *op,
                                                           const framework::Clock& clock,
                                                           metrics::DoubleAverageMetric& latency_metric)
    : _result_handler(nullptr),
      _state(std::move(state)),
      _doc_id(std::move(doc_id)),
      _throttle_token(std::move(throttle_token)),
      _op(op),
      _start_time(clock),
      _latency_metric(latency_metric)
{
}

ApplyBucketDiffEntryComplete::~ApplyBucketDiffEntryComplete() = default;

void
ApplyBucketDiffEntryComplete::onComplete(std::unique_ptr<spi::Result> result) noexcept
{
    if (_result_handler != nullptr) {
        _result_handler->handle(*result);
    }
    double elapsed = _start_time.getElapsedTimeAsDouble();
    _latency_metric.addValue(elapsed);
    _throttle_token.reset();
    _state->on_entry_complete(std::move(result), _doc_id, _op);
}

void
ApplyBucketDiffEntryComplete::addResultHandler(const spi::ResultHandler* resultHandler)
{
    assert(_result_handler == nullptr);
    _result_handler = resultHandler;
}

}
