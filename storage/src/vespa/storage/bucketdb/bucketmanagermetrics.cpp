// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmanagermetrics.h"
#include <vespa/vespalib/util/exceptions.h>
#include <cassert>

namespace storage {

using vespalib::IllegalStateException;
using vespalib::make_string;

DataStoredMetrics::DataStoredMetrics(const std::string& name, metrics::MetricSet* owner)
    : metrics::MetricSet(name, {{"partofsum"},{"yamasdefault"}}, "", owner),
      buckets("buckets", {}, "buckets managed", this),
      docs("docs", {}, "documents stored", this),
      bytes("bytes", {}, "bytes stored", this),
      active("activebuckets", {}, "Number of active buckets on the node", this),
      ready("readybuckets", {}, "Number of ready buckets on the node", this)
{
    docs.logOnlyIfSet();
    bytes.logOnlyIfSet();
    active.logOnlyIfSet();
    ready.logOnlyIfSet();
}

DataStoredMetrics::~DataStoredMetrics() { }

BucketManagerMetrics::BucketManagerMetrics()
    : metrics::MetricSet("datastored", {}, ""),
      disks(),
      total("alldisks", {{"sum"}}, "Sum of data stored metrics for all disks", this),
      simpleBucketInfoRequestSize("simplebucketinforeqsize", {},
            "Amount of buckets returned in simple bucket info requests",
            this),
      fullBucketInfoRequestSize("fullbucketinforeqsize", {},
            "Amount of distributors answered at once in full bucket info requests.", this),
      fullBucketInfoLatency("fullbucketinfolatency", {},
            "Amount of time spent to process a full bucket info request", this)
{ }

BucketManagerMetrics::~BucketManagerMetrics() { }

void
BucketManagerMetrics::setDisks(uint16_t numDisks) {
    assert(numDisks > 0);
    if (!disks.empty()) {
        throw IllegalStateException("Cannot initialize disks twice", VESPA_STRLOC);
    }
    for (uint16_t i = 0; i<numDisks; i++) {
        disks.push_back(DataStoredMetrics::SP(
                new DataStoredMetrics(make_string("disk%d", i), this)));
        total.addMetricToSum(*disks.back());
    }
}

}
