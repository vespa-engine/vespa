// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "shared_operation_throttler.h"
#include <vespa/document/base/documentid.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/persistence/spi/operationcomplete.h>
#include <vespa/storageframework/generic/clock/timer.h>
#include <future>

namespace storage {

class ApplyBucketDiffState;

/*
 * Complete handler for a bucket diff entry spi operation (putAsync
 * or removeAsync)
 */
class ApplyBucketDiffEntryComplete : public spi::OperationComplete
{
    const spi::ResultHandler*             _result_handler;
    std::shared_ptr<ApplyBucketDiffState> _state;
    document::DocumentId                  _doc_id;
    ThrottleToken                         _throttle_token;
    const char*                           _op;
    framework::MilliSecTimer              _start_time;
    metrics::DoubleAverageMetric&         _latency_metric;
public:
    ApplyBucketDiffEntryComplete(std::shared_ptr<ApplyBucketDiffState> state,
                                 document::DocumentId doc_id,
                                 ThrottleToken throttle_token,
                                 const char *op, const framework::Clock& clock,
                                 metrics::DoubleAverageMetric& latency_metric);
    ~ApplyBucketDiffEntryComplete() override;
    void onComplete(std::unique_ptr<spi::Result> result) noexcept override;
    void addResultHandler(const spi::ResultHandler* resultHandler) override;
};

}
