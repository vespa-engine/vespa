// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/metrics/metrics.h>

namespace storage {
namespace memfile {

class SerializationWriteMetrics : public metrics::MetricSet
{
public:
    metrics::DoubleAverageMetric headerLatency;
    metrics::LongAverageMetric headerSize;
    metrics::DoubleAverageMetric bodyLatency;
    metrics::LongAverageMetric bodySize;
    metrics::DoubleAverageMetric metaLatency;
    metrics::LongAverageMetric metaSize;
    metrics::DoubleAverageMetric totalLatency;

    SerializationWriteMetrics(const std::string& name, MetricSet& owner);
    ~SerializationWriteMetrics();
};

class SerializationMetrics : public metrics::MetricSet
{
public:
    metrics::DoubleAverageMetric initialMetaReadLatency;
    metrics::DoubleAverageMetric tooLargeMetaReadLatency;
    metrics::DoubleAverageMetric totalLoadFileLatency;
    metrics::DoubleAverageMetric verifyLatency;
    metrics::DoubleAverageMetric deleteFileLatency;
    metrics::DoubleAverageMetric headerReadLatency;
    metrics::LongAverageMetric headerReadSize;
    metrics::DoubleAverageMetric bodyReadLatency;
    metrics::LongAverageMetric bodyReadSize;
    metrics::DoubleAverageMetric cacheUpdateAndImplicitVerifyLatency;
    metrics::LongCountMetric fullRewritesDueToDownsizingFile;
    metrics::LongCountMetric fullRewritesDueToTooSmallFile;
    SerializationWriteMetrics partialWrite;
    SerializationWriteMetrics fullWrite;

    SerializationMetrics(const std::string& name, MetricSet* owner = 0);
    ~SerializationMetrics();
};

} // memfile
} // storage
