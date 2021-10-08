// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "gauge.h"
#include "metrics_manager.h"

namespace vespalib {
namespace metrics {

void
Gauge::sample(double value, Point point) const
{
    if (_manager) {
        _manager->sample(Measurement(std::make_pair(_id, point), value));
    }
}

} // namespace vespalib::metrics
} // namespace vespalib
