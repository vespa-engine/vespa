// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/metrics/valuemetric.h>
#include <vespa/metrics/metricset.h>

namespace storage {

struct VisitorThreadMetrics : public metrics::MetricSet
{
    using DoubleAverageMetric = metrics::DoubleAverageMetric;
    using LongAverageMetric = metrics::LongAverageMetric;

    LongAverageMetric queueSize;
    DoubleAverageMetric averageQueueWaitingTime;
    DoubleAverageMetric averageVisitorLifeTime;
    DoubleAverageMetric averageVisitorCreationTime;
    DoubleAverageMetric averageMessageSendTime;
    DoubleAverageMetric averageProcessingTime;
    LongAverageMetric createdVisitors;
    LongAverageMetric abortedVisitors;
    LongAverageMetric completedVisitors;
    LongAverageMetric failedVisitors;
    LongAverageMetric visitorDestinationFailureReplies;

    VisitorThreadMetrics(const std::string& name, const std::string& desc);
    ~VisitorThreadMetrics() override;
};

}
