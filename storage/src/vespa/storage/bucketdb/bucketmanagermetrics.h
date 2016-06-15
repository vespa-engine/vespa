// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

    DataStoredMetrics(const std::string& name, metrics::MetricSet* owner)
        : metrics::MetricSet(name, "partofsum yamasdefault", "", owner, "disk"),
          buckets("buckets", "", "buckets managed", this),
          docs("docs", "", "documents stored", this),
          bytes("bytes", "", "bytes stored", this),
          active("activebuckets", "", "Number of active buckets on the node",
                 this),
          ready("readybuckets", "", "Number of ready buckets on the node",
                 this)
    {
        docs.logOnlyIfSet();
        bytes.logOnlyIfSet();
        active.logOnlyIfSet();
        ready.logOnlyIfSet();
    }
};

class BucketManagerMetrics : public metrics::MetricSet
{
public:
    std::vector<std::shared_ptr<DataStoredMetrics> > disks;
    metrics::SumMetric<metrics::MetricSet> total;
    metrics::LongValueMetric simpleBucketInfoRequestSize;
    metrics::LongAverageMetric fullBucketInfoRequestSize;
    metrics::LongAverageMetric fullBucketInfoLatency;

    BucketManagerMetrics()
        : metrics::MetricSet("datastored", "", ""),
          disks(),
          total("alldisks", "sum",
                "Sum of data stored metrics for all disks", this),
          simpleBucketInfoRequestSize("simplebucketinforeqsize", "",
                "Amount of buckets returned in simple bucket info requests",
                this),
          fullBucketInfoRequestSize("fullbucketinforeqsize", "",
                "Amount of distributors answered at once in full bucket "
                "info requests.", this),
          fullBucketInfoLatency("fullbucketinfolatency", "",
                "Amount of time spent to process a full bucket info request",
                this)

    {
    }

    void setDisks(uint16_t numDisks) {
        assert(numDisks > 0);
        if (!disks.empty()) {
            throw vespalib::IllegalStateException(
                    "Cannot initialize disks twice", VESPA_STRLOC);
        }
        for (uint16_t i = 0; i<numDisks; i++) {
            disks.push_back(DataStoredMetrics::SP(
                    new DataStoredMetrics(
                        vespalib::make_string("disk%d", i), this)));
            total.addMetricToSum(*disks.back());
        }
    }
};

}


