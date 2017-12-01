// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "metric_types.h"
#include <assert.h>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.metrics.metric_types");

namespace vespalib {
namespace metrics {

const char* MetricTypes::_typeNames[] = {
    "NONE",
    "Counter",
    "Gauge",
    "Histogram",
    "IntegerHistogram"
};

void
MetricTypes::check(size_t id, const vespalib::string &name, MetricType ty)
{
    std::lock_guard<std::mutex> guard(_lock);
    if (id < _seen.size()) {
        MetricType old = _seen[id];
        if (old == ty) {
            return;
        }
        LOG(warning, "metric '%s' with different types %s and %s, this will be confusing",
            name.c_str(), _typeNames[ty], _typeNames[old]);
    }
    assert (_seen.size() == id);
    _seen.push_back(ty);
}

} // namespace vespalib::metrics
} // namespace vespalib



