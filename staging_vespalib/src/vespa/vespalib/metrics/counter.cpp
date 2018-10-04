// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "counter.h"
#include "metrics_manager.h"

namespace vespalib {
namespace metrics {


void
Counter::add(size_t count, Point point) const
{
    if (_manager) {
        MetricPointId fullId(_id, point);
        _manager->add(Increment(fullId, count));
    }
}

} // namespace vespalib::metrics
} // namespace vespalib
