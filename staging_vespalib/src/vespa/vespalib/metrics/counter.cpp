// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "counter.h"
#include "metrics_collector.h"

namespace vespalib {
namespace metrics {

void
Counter::add()
{
    add(1);
}

void
Counter::add(Point p)
{
    add(1, p);
}

void
Counter::add(size_t count)
{
    if (_manager) {
        _manager->add(CounterIncrement(ident(), count));
    }
}

void
Counter::add(size_t count, Point point)
{
    if (_manager) {
        MetricIdentifier id(_idx.name_idx, point.id());
        _manager->add(CounterIncrement(id, count));
    }
}

} // namespace vespalib::metrics
} // namespace vespalib
