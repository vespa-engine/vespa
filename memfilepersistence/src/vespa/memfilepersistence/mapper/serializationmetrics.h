// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/metrics/metrics.h>

namespace storage {
namespace memfile {

class SerializationWriteMetrics : public metrics::MetricSet
{
public:
    metrics::LongAverageMetric headerLatency;
    metrics::LongAverageMetric headerSize;
    metrics::LongAverageMetric bodyLatency;
    metrics::LongAverageMetric bodySize;
    metrics::LongAverageMetric metaLatency;
    metrics::LongAverageMetric metaSize;
    metrics::LongAverageMetric totalLatency;

    SerializationWriteMetrics(const std::string& name, MetricSet& owner);
    ~SerializationWriteMetrics();
};

class SerializationMetrics : public metrics::MetricSet
{
public:
    metrics::LongAverageMetric initialMetaReadLatency;
    metrics::LongAverageMetric tooLargeMetaReadLatency;
    metrics::LongAverageMetric totalLoadFileLatency;
    metrics::LongAverageMetric verifyLatency;
    metrics::LongAverageMetric deleteFileLatency;
    metrics::LongAverageMetric headerReadLatency;
    metrics::LongAverageMetric headerReadSize;
    metrics::LongAverageMetric bodyReadLatency;
    metrics::LongAverageMetric bodyReadSize;
    metrics::LongAverageMetric cacheUpdateAndImplicitVerifyLatency;
    metrics::LongCountMetric fullRewritesDueToDownsizingFile;
    metrics::LongCountMetric fullRewritesDueToTooSmallFile;
    SerializationWriteMetrics partialWrite;
    SerializationWriteMetrics fullWrite;

    SerializationMetrics(const std::string& name, MetricSet* owner = 0);
    ~SerializationMetrics();
};

} // memfile
} // storage
