// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "dummy_metrics_collector.h"

namespace vespalib {
namespace metrics {

Snapshot
DummyMetricsCollector::snapshot()
{
    clock::time_point endTime = clock::now();
    auto s = _startTime.time_since_epoch();
    auto ss = std::chrono::duration_cast<std::chrono::microseconds>(s);
    auto e = endTime.time_since_epoch();
    auto ee = std::chrono::duration_cast<std::chrono::microseconds>(e);

    Snapshot snap(ss.count() * 0.000001, ee.count() * 0.000001);
    return snap;
}

} // namespace vespalib::metrics
} // namespace vespalib
