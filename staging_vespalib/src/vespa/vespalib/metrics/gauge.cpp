// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "gauge.h"
#include "metrics_collector.h"

namespace vespalib {
namespace metrics {

void
Gauge::sample(double value) const
{
    if (_manager) {
        _manager->sample(GaugeMeasurement(ident(), value));
    }
}

void
Gauge::sample(double value, Point point) const
{
    if (_manager) {
        MetricIdentifier id(_idx.name_idx, point.id());
        _manager->sample(GaugeMeasurement(id, value));
    }
}

} // namespace vespalib::metrics
} // namespace vespalib
