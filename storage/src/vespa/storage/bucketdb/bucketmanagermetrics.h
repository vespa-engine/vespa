// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketspace.h>
#include <vespa/metrics/common/memory_usage_metrics.h>
#include <vespa/metrics/summetric.h>

#include <unordered_map>
#include <memory>

namespace storage {

struct DataStoredMetrics : metrics::MetricSet {
    typedef std::shared_ptr<DataStoredMetrics> SP;

    metrics::LongValueMetric buckets;
    metrics::LongValueMetric docs;
    metrics::LongValueMetric bytes;
    metrics::LongValueMetric active;
    metrics::LongValueMetric ready;

    DataStoredMetrics(const std::string& name, metrics::MetricSet* owner);
    ~DataStoredMetrics() override;
};

struct ContentBucketDbMetrics : metrics::MetricSet {
    explicit ContentBucketDbMetrics(metrics::MetricSet* owner);
    ~ContentBucketDbMetrics() override;

    metrics::MemoryUsageMetrics memory_usage;
};

struct BucketSpaceMetrics : metrics::MetricSet {
    // Superficially very similar to DataStoredMetrics, but metric naming and dimensions differ
    metrics::LongValueMetric buckets_total;
    metrics::LongValueMetric docs;
    metrics::LongValueMetric bytes;
    metrics::LongValueMetric active_buckets;
    metrics::LongValueMetric ready_buckets;
    ContentBucketDbMetrics bucket_db_metrics;

    BucketSpaceMetrics(const vespalib::string& space_name, metrics::MetricSet* owner);
    ~BucketSpaceMetrics() override;
};

class ContentBucketSpaceRepo;

class BucketManagerMetrics : public metrics::MetricSet {
public:
    std::shared_ptr<DataStoredMetrics> disk;
    using BucketSpaceMap = std::unordered_map<document::BucketSpace, std::unique_ptr<BucketSpaceMetrics>, document::BucketSpace::hash>;
    BucketSpaceMap bucket_spaces;
    metrics::SumMetric<metrics::MetricSet> total;
    metrics::LongValueMetric simpleBucketInfoRequestSize;
    metrics::LongAverageMetric fullBucketInfoRequestSize;
    metrics::LongAverageMetric fullBucketInfoLatency;

    explicit BucketManagerMetrics(const ContentBucketSpaceRepo& repo);
    ~BucketManagerMetrics() override;
};

}


