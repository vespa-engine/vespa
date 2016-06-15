// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/memfilepersistence/mapper/serializationmetrics.h>

namespace storage {
namespace memfile {

class MemFilePersistenceThreadMetrics : public metrics::MetricSet
{
public:
    metrics::LongCountMetric headerOnlyGets;
    metrics::LongCountMetric headerOnlyUpdates;
    SerializationMetrics serialization;

    MemFilePersistenceThreadMetrics(const std::string& name, metrics::MetricSet& owner)
        : metrics::MetricSet(name, "partofsum thread",
                             "Metrics for a worker thread using memfile persistence "
                             "provider", &owner),
          headerOnlyGets("headeronlygets", "",
                         "Number of gets that only read header", this),
          headerOnlyUpdates("headeronlyupdates", "",
                            "Number of updates that only wrote header", this),
          serialization("serialization", this)
    {
    }
};

class MemFilePersistenceCacheMetrics : public metrics::MetricSet
{
public:
    metrics::LongValueMetric files;
    metrics::LongValueMetric meta;
    metrics::LongValueMetric header;
    metrics::LongValueMetric body;
    metrics::LongCountMetric hits;
    metrics::LongCountMetric misses;
    metrics::LongCountMetric meta_evictions;
    metrics::LongCountMetric header_evictions;
    metrics::LongCountMetric body_evictions;

    MemFilePersistenceCacheMetrics(metrics::MetricSet& owner)
        : metrics::MetricSet("cache", "",
                             "Metrics for the VDS persistence cache", &owner),
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
    {}
};

class MemFilePersistenceMetrics : public metrics::MetricSet
{
    framework::Component& _component;

public:
    vespalib::Lock _threadMetricsLock;
    std::list<vespalib::LinkedPtr<MemFilePersistenceThreadMetrics> > _threadMetrics;

    std::unique_ptr<metrics::SumMetric<MemFilePersistenceThreadMetrics> > _sumMetric;
    MemFilePersistenceCacheMetrics _cache;

    MemFilePersistenceMetrics(framework::Component& component)
        : metrics::MetricSet("memfilepersistence", "",
                             "Metrics for the VDS persistence layer"),
          _component(component),
          _cache(*this)
    {
    }

    MemFilePersistenceThreadMetrics* addThreadMetrics() {
        metrics::MetricLockGuard metricLock(_component.getMetricManagerLock());
        vespalib::LockGuard guard(_threadMetricsLock);

        if (!_sumMetric.get()) {
            _sumMetric.reset(new metrics::SumMetric<MemFilePersistenceThreadMetrics>
                             ("allthreads", "sum", "", this));
        }

        std::string name = vespalib::make_string("thread_%zu", _threadMetrics.size());

        MemFilePersistenceThreadMetrics* metrics =
            new MemFilePersistenceThreadMetrics(name, *this);

        _threadMetrics.push_back(vespalib::LinkedPtr<MemFilePersistenceThreadMetrics>(
                                         metrics));
        _sumMetric->addMetricToSum(*metrics);
        return metrics;
    }
};

}
}

