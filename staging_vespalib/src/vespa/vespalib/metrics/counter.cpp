// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "counter.h"
#include "metrics_collector.h"

namespace vespalib {
namespace metrics {

void
Counter::add()
{
    _manager->add(CounterIncrement(id(), 1));
}

void
Counter::add(size_t count)
{
    _manager->add(CounterIncrement(id(), count));
}

} // namespace vespalib::metrics
} // namespace vespalib
