// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmanagermetrics.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/common/content_bucket_space_repo.h>
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

DataStoredMetrics::~DataStoredMetrics() = default;

BucketSpaceMetrics::BucketSpaceMetrics(const vespalib::string& space_name, metrics::MetricSet* owner)
        : metrics::MetricSet("bucket_space", {{"bucketSpace", space_name}}, "", owner),
          buckets_total("buckets_total", {}, "Total number buckets present in the bucket space (ready + not ready)", this),
          docs("docs", {}, "Documents stored in the bucket space", this),
          bytes("bytes", {}, "Bytes stored across all documents in the bucket space", this),
          active_buckets("active_buckets", {}, "Number of active buckets in the bucket space", this),
          ready_buckets("ready_buckets", {}, "Number of ready buckets in the bucket space", this)
{
    docs.logOnlyIfSet();
    bytes.logOnlyIfSet();
    active_buckets.logOnlyIfSet();
    ready_buckets.logOnlyIfSet();
}

BucketSpaceMetrics::~BucketSpaceMetrics() = default;

BucketManagerMetrics::BucketManagerMetrics(const ContentBucketSpaceRepo& repo)
    : metrics::MetricSet("datastored", {}, ""),
      disks(),
      bucket_spaces(),
      total("alldisks", {{"sum"}}, "Sum of data stored metrics for all disks", this),
      simpleBucketInfoRequestSize("simplebucketinforeqsize", {},
            "Amount of buckets returned in simple bucket info requests",
            this),
      fullBucketInfoRequestSize("fullbucketinforeqsize", {},
            "Amount of distributors answered at once in full bucket info requests.", this),
      fullBucketInfoLatency("fullbucketinfolatency", {},
            "Amount of time spent to process a full bucket info request", this)
{
    for (const auto& space : repo) {
        bucket_spaces.emplace(space.first, std::make_unique<BucketSpaceMetrics>(
                document::FixedBucketSpaces::to_string(space.first), this));
    }
}

BucketManagerMetrics::~BucketManagerMetrics() = default;

void
BucketManagerMetrics::setDisks(uint16_t numDisks) {
    assert(numDisks > 0);
    if (!disks.empty()) {
        throw IllegalStateException("Cannot initialize disks twice", VESPA_STRLOC);
    }
    for (uint16_t i = 0; i<numDisks; i++) {
        disks.push_back(std::make_shared<DataStoredMetrics>(make_string("disk%d", i), this));
        total.addMetricToSum(*disks.back());
    }
}

}
