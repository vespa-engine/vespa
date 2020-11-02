// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/persistence/spi/operationcomplete.h>
#include <vespa/storageframework/generic/clock/timer.h>
#include <vespa/metrics/valuemetric.h>
#include <future>

namespace storage {

/*
 * Complete handler for a bucket diff entry spi operation (putAsync
 * or removeAsync)
 */
class ApplyBucketDiffEntryComplete : public spi::OperationComplete
{
    using ResultPromise = std::promise<std::unique_ptr<spi::Result>>;
    const spi::ResultHandler*     _result_handler;
    ResultPromise                 _result_promise;
    framework::MilliSecTimer      _start_time;
    metrics::DoubleAverageMetric& _latency_metric;
public:
    ApplyBucketDiffEntryComplete(ResultPromise result_promise, const framework::Clock& clock, metrics::DoubleAverageMetric& latency_metric);
    ~ApplyBucketDiffEntryComplete();
    void onComplete(std::unique_ptr<spi::Result> result) override;
    void addResultHandler(const spi::ResultHandler* resultHandler) override;
};

}
