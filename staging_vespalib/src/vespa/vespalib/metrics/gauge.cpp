// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "gauge.h"
#include "metrics_manager.h"

namespace vespalib {
namespace metrics {

void
Gauge::sample(double value, Point point) const
{
    if (_manager) {
        MetricPointId fullId(_id, point);
        _manager->sample(Measurement(fullId, value));
    }
}

} // namespace vespalib::metrics
} // namespace vespalib
