// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class CommunicationManagerMetrics
 * \ingroup storageserver
 *
 * \brief Metrics for the communication manager.
 */

#pragma once

#include <vespa/metrics/metricset.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/metrics/countmetric.h>

namespace storage {

struct CommunicationManagerMetrics : public metrics::MetricSet {
    metrics::LongAverageMetric queueSize;
    metrics::DoubleAverageMetric messageProcessTime;
    metrics::DoubleAverageMetric exceptionMessageProcessTime;
    metrics::LongCountMetric failedDueToTooLittleMemory;
    metrics::LongCountMetric convertToStorageAPIFailures;
    metrics::LongCountMetric bucketSpaceMappingFailures;
    metrics::DoubleAverageMetric sendCommandLatency;
    metrics::DoubleAverageMetric sendReplyLatency;

    CommunicationManagerMetrics(metrics::MetricSet* owner = nullptr);
    ~CommunicationManagerMetrics();
};

}

