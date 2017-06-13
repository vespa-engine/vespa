// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metrics.h>

namespace storage {

struct DataStoredMetrics : public metrics::MetricSet
{
    typedef std::shared_ptr<DataStoredMetrics> SP;

    metrics::LongValueMetric buckets;
    metrics::LongValueMetric docs;
    metrics::LongValueMetric bytes;
    metrics::LongValueMetric active;
    metrics::LongValueMetric ready;

    DataStoredMetrics(const std::string& name, metrics::MetricSet* owner);
    ~DataStoredMetrics();
};

class BucketManagerMetrics : public metrics::MetricSet
{
public:
    std::vector<std::shared_ptr<DataStoredMetrics> > disks;
    metrics::SumMetric<metrics::MetricSet> total;
    metrics::LongValueMetric simpleBucketInfoRequestSize;
    metrics::LongAverageMetric fullBucketInfoRequestSize;
    metrics::LongAverageMetric fullBucketInfoLatency;

    BucketManagerMetrics();
    ~BucketManagerMetrics();
    void setDisks(uint16_t numDisks);
};

}


