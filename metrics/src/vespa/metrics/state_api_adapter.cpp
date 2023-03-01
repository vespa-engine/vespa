// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "jsonwriter.h"
#include "state_api_adapter.h"
#include "metricmanager.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace metrics {

vespalib::string
StateApiAdapter::getMetrics(const vespalib::string &consumer)
{
    MetricLockGuard guard(_manager.getMetricLock());
    auto periods = _manager.getSnapshotPeriods(guard);
    if (periods.empty()) {
        return ""; // no configuration yet
    }
    const MetricSnapshot &snapshot(_manager.getMetricSnapshot(guard, periods[0]));
    vespalib::asciistream json;
    vespalib::JsonStream stream(json);
    metrics::JsonWriter metricJsonWriter(stream);
    _manager.visit(guard, snapshot, metricJsonWriter, consumer);
    stream.finalize();
    return json.str();
}

vespalib::string
StateApiAdapter::getTotalMetrics(const vespalib::string &consumer)
{
    _manager.updateMetrics();
    MetricLockGuard guard(_manager.getMetricLock());
    _manager.checkMetricsAltered(guard);
    system_time currentTime = vespalib::system_clock::now();
    auto generated = std::make_unique<MetricSnapshot>("Total metrics from start until current time", 0s,
                                                      _manager.getTotalMetricSnapshot(guard).getMetrics(), true);
    _manager.getActiveMetrics(guard).addToSnapshot(*generated, false, currentTime);
    generated->setFromTime(_manager.getTotalMetricSnapshot(guard).getFromTime());
    const MetricSnapshot &snapshot = *generated;
    vespalib::asciistream json;
    vespalib::JsonStream stream(json);
    metrics::JsonWriter metricJsonWriter(stream);
    _manager.visit(guard, snapshot, metricJsonWriter, consumer);
    stream.finalize();
    return json.str();
}

} // namespace metrics
