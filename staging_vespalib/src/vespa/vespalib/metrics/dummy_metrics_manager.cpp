// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "dummy_metrics_manager.h"

namespace vespalib {
namespace metrics {

DummyMetricsManager::~DummyMetricsManager() {}

Snapshot
DummyMetricsManager::snapshot()
{
    InternalTimeStamp endTime = now_stamp();
    std::chrono::microseconds s = since_epoch(_startTime);
    std::chrono::microseconds e = since_epoch(endTime);
    const double micro = 0.000001;
    Snapshot snap(s.count() * micro, e.count() * micro);
    return snap;
}

} // namespace vespalib::metrics
} // namespace vespalib
