// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/memfilepersistence/mapper/serializationmetrics.h>
#include <vespa/vespalib/util/sync.h>
#include <list>

namespace storage {

namespace framework { class Component; }

namespace memfile {

class MemFilePersistenceThreadMetrics : public metrics::MetricSet
{
public:
    metrics::LongCountMetric headerOnlyGets;
    metrics::LongCountMetric headerOnlyUpdates;
    SerializationMetrics serialization;

    MemFilePersistenceThreadMetrics(const std::string& name, metrics::MetricSet& owner);
    ~MemFilePersistenceThreadMetrics();
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

    MemFilePersistenceCacheMetrics(metrics::MetricSet& owner);
    ~MemFilePersistenceCacheMetrics();
};

class MemFilePersistenceMetrics : public metrics::MetricSet
{
    framework::Component& _component;

public:
    vespalib::Lock _threadMetricsLock;
    std::list<std::unique_ptr<MemFilePersistenceThreadMetrics> > _threadMetrics;

    std::unique_ptr<metrics::SumMetric<MemFilePersistenceThreadMetrics> > _sumMetric;
    MemFilePersistenceCacheMetrics _cache;

    MemFilePersistenceMetrics(framework::Component& component);
    ~MemFilePersistenceMetrics();
    MemFilePersistenceThreadMetrics* addThreadMetrics();
};

}
}

