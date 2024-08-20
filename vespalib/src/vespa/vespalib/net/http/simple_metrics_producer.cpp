// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_metrics_producer.h"

namespace vespalib {

SimpleMetricsProducer::SimpleMetricsProducer()
    : _lock(),
      _metrics(),
      _total_metrics()
{
}

SimpleMetricsProducer::~SimpleMetricsProducer() = default;

void
SimpleMetricsProducer::setMetrics(const std::string &metrics, ExpositionFormat format)
{
    std::lock_guard guard(_lock);
    _metrics[format] = metrics;
}

std::string
SimpleMetricsProducer::getMetrics(const std::string &, ExpositionFormat format)
{
    std::lock_guard guard(_lock);
    return _metrics[format]; // May implicitly create entry, but that's fine here.
}

void
SimpleMetricsProducer::setTotalMetrics(const std::string &metrics, ExpositionFormat format)
{
    std::lock_guard guard(_lock);
    _total_metrics[format] = metrics;
}

std::string
SimpleMetricsProducer::getTotalMetrics(const std::string &, ExpositionFormat format)
{
    std::lock_guard guard(_lock);
    return _total_metrics[format];
}

} // namespace vespalib
