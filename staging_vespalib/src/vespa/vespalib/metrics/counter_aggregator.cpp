// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "counter_aggregator.h"
#include <assert.h>
#include <map>

namespace vespalib {
namespace metrics {

CounterAggregator::CounterAggregator(MetricIdentifier id)
  : idx(id), count(0)
{}

void
CounterAggregator::merge(const Counter::Increment &increment)
{
    assert(idx == increment.idx);
    count += increment.value;
}

void
CounterAggregator::merge(const CounterAggregator &other)
{
    assert(idx == other.idx);
    count += other.count;
}

} // namespace vespalib::metrics
} // namespace vespalib
