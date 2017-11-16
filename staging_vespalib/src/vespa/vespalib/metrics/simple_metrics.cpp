// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "simple_metrics.h"
#include "simple_metrics_collector.h"

namespace vespalib {
namespace metrics {

void
Counter::add()
{
    _manager->add(CounterIncrement(_idx, 1));
}

void
Counter::add(size_t count)
{
    _manager->add(CounterIncrement(_idx, count));
}

void
Gauge::sample(double value)
{
    _manager->sample(GaugeMeasurement(_idx, value));
}

} // namespace vespalib::metrics
} // namespace vespalib
