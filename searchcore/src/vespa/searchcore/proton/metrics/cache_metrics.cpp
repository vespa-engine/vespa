// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cache_metrics.h"
#include <vespa/vespalib/stllike/cache_stats.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.metrics.cache_metrics");

using vespalib::CacheStats;

namespace proton {

CacheMetrics::CacheMetrics(MetricSet *parent, const std::string& name, const std::string& description,
                           const std::string& cache_name)
    : MetricSet(name, {}, description, parent),
      memoryUsage("memory_usage", {}, "Memory usage of the cache (in bytes)", this),
      elements("elements", {}, "Number of elements in the cache", this),
      hitRate("hit_rate", {}, "Rate of hits in the cache compared to number of lookups", this),
      lookups("lookups", {}, "Number of lookups in the cache (hits + misses)", this),
      invalidations("invalidations", {}, "Number of invalidations (erased elements) in the cache.", this),
      _cache_name(cache_name),
      _last_stats()
{
}

CacheMetrics::~CacheMetrics() = default;

void
CacheMetrics::update_hit_rate(const CacheStats& current, const CacheStats& last)
{
    if (current.lookups() < last.lookups() || current.hits < last.hits) {
        LOG(warning, "Not adding %s cache hit rate metrics as values calculated "
                     "are corrupt. current.lookups=%zu, last.lookups=%zu, current.hits=%zu, last.hits=%zu.",
            _cache_name.c_str(), current.lookups(), last.lookups(), current.hits, last.hits);
    } else {
        if ((current.lookups() - last.lookups()) > 0xffffffffull
            || (current.hits - last.hits) > 0xffffffffull)
        {
            LOG(warning, "%s cache hit rate metrics to add are suspiciously high."
                         " lookups diff=%zu, hits diff=%zu.",
                _cache_name.c_str(), current.lookups() - last.lookups(), current.hits - last.hits);
        }
        hitRate.addTotalValueWithCount(current.hits - last.hits, current.lookups() - last.lookups());
    }
}

void
CacheMetrics::update_count_metric(uint64_t currVal, uint64_t lastVal, metrics::LongCountMetric& metric)
{
    uint64_t delta = (currVal >= lastVal) ? (currVal - lastVal) : 0;
    metric.inc(delta);
}

void
CacheMetrics::update_metrics(const CacheStats& stats)
{
    memoryUsage.set(stats.memory_used);
    elements.set(stats.elements);
    update_hit_rate(stats, _last_stats);
    update_count_metric(stats.lookups(), _last_stats.lookups(), lookups);
    update_count_metric(stats.invalidations, _last_stats.invalidations, invalidations);
    _last_stats = stats;
}

}
