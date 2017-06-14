// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "memfilepersistenceprovidermetrics.h"
#include <vespa/metrics/summetric.hpp>
#include <vespa/storageframework/generic/component/component.h>

namespace storage {
namespace memfile {

using metrics::MetricSet;

MemFilePersistenceThreadMetrics::MemFilePersistenceThreadMetrics(const std::string& name, MetricSet& owner)
    : MetricSet(name, "partofsum thread", "Metrics for a worker thread using memfile persistence provider", &owner),
      headerOnlyGets("headeronlygets", "", "Number of gets that only read header", this),
      headerOnlyUpdates("headeronlyupdates", "", "Number of updates that only wrote header", this),
      serialization("serialization", this)
{ }

MemFilePersistenceThreadMetrics::~MemFilePersistenceThreadMetrics() { }

MemFilePersistenceCacheMetrics::MemFilePersistenceCacheMetrics(MetricSet& owner)
    : MetricSet("cache", "", "Metrics for the VDS persistence cache", &owner),
      files("files", "", "Number of files cached", this),
      meta("meta", "", "Bytes of file metadata cached", this),
      header("header", "", "Bytes of file header parts cached", this),
      body("body", "", "Bytes of file body parts cached", this),
      hits("hits", "", "Number of times a bucket was attempted fetched "
           "from the cache and it was already present", this),
      misses("misses", "", "Number of times a bucket was attempted fetched "
             "from the cache and it could not be found, requiring a load", this),
      meta_evictions("meta_evictions", "", "Bucket meta data evictions", this),
      header_evictions("header_evictions", "", "Bucket header (and "
                       "implicitly body, if present) data evictions", this),
      body_evictions("body_evictions", "", "Bucket body data evictions", this)
{ }

MemFilePersistenceCacheMetrics::~MemFilePersistenceCacheMetrics() { }

MemFilePersistenceMetrics::MemFilePersistenceMetrics(framework::Component& component)
    : MetricSet("memfilepersistence", "", "Metrics for the VDS persistence layer"),
      _component(component),
      _cache(*this)
{ }

MemFilePersistenceMetrics::~MemFilePersistenceMetrics() { }

MemFilePersistenceThreadMetrics*
MemFilePersistenceMetrics::addThreadMetrics() {
    vespalib::MonitorGuard metricLock(_component.getMetricManagerLock());
    vespalib::LockGuard guard(_threadMetricsLock);

    if (!_sumMetric.get()) {
        _sumMetric.reset(new metrics::SumMetric<MemFilePersistenceThreadMetrics>
                         ("allthreads", "sum", "", this));
    }

    std::string name = vespalib::make_string("thread_%zu", _threadMetrics.size());
    MemFilePersistenceThreadMetrics * metrics = new MemFilePersistenceThreadMetrics(name, *this);
    _threadMetrics.emplace_back(metrics);
    _sumMetric->addMetricToSum(*metrics);
    return metrics;
}

}
}

template class metrics::SumMetric<storage::memfile::MemFilePersistenceThreadMetrics>;
