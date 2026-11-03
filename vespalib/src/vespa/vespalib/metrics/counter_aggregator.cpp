// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "counter_aggregator.h"

#include <vespa/check_require.h>

#include <map>

namespace vespalib {
namespace metrics {

CounterAggregator::CounterAggregator(const Counter::Increment& increment)
    : idx(increment.idx), count(increment.value) {
}

void CounterAggregator::merge(const CounterAggregator& other) {
    CHECK(idx == other.idx);
    count += other.count;
}

} // namespace metrics
} // namespace vespalib
