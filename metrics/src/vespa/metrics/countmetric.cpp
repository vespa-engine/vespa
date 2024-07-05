// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "countmetric.hpp"
#include <vespa/vespalib/stllike/asciistream.h>

#include <vespa/log/log.h>

LOG_SETUP(".metrics.metric.count");

namespace metrics {

void
AbstractCountMetric::logWarning(const char* msg, const char * op) const
{
    vespalib::asciistream ost;
    ost << msg << " in count metric " << getPath() << " op " << op << ". Resetting it.";
    LOG(warning, "%s", ost.str().data());
}

template class CountMetric<uint64_t, true>;

} // metrics
