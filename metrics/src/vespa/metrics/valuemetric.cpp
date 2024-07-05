// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "valuemetric.hpp"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/log/log.h>

LOG_SETUP(".metrics.metric.value");

namespace metrics {

namespace {

std::atomic<bool> hasWarned {false};

}

void
AbstractValueMetric::logWarning(const char* msg, const char * op) const
{
    vespalib::asciistream ost;
    ost << msg << " in value metric " << getPath() << " op " << op << ". Resetting it.";
    LOG(warning, "%s", ost.str().data());
}

void
AbstractValueMetric::logNonFiniteValueWarning() const
{
    if (hasWarned.exchange(true, std::memory_order_relaxed) == false) {
        LOG(warning,
            "Metric '%s' attempted updated with a value that is NaN or "
            "Infinity; update ignored! No further warnings will be printed for "
            "such updates on any metrics, but they can be observed with debug "
            "logging enabled on component 'metrics.metric.value'.",
            getPath().c_str());
    } else {
        LOG(debug,
            "Metric '%s' attempted updated with a value that is NaN/Infinity; "
            "update ignored!",
            getPath().c_str());
    }
}

template class ValueMetric<double, double, true>;
template class ValueMetric<double, double, false>;
template class ValueMetric<int64_t, int64_t, true>;
template class ValueMetric<int64_t, int64_t, false>;

} // metrics
