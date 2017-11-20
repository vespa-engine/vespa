// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "gauge.h"
#include "metrics_collector.h"

namespace vespalib {
namespace metrics {

void
Gauge::sample(double value)
{
    _manager->sample(GaugeMeasurement(id(), value));
}

} // namespace vespalib::metrics
} // namespace vespalib
