// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "jsonwriter.h"
#include "state_api_adapter.h"
#include "metricmanager.h"
#include <vespa/vespalib/stllike/asciistream.h>


namespace metrics {

vespalib::string
StateApiAdapter::getMetrics(const vespalib::string &consumer)
{
    metrics::MetricLockGuard guard(_manager.getMetricLock());
    std::vector<uint32_t> periods = _manager.getSnapshotPeriods(guard);
    if (periods.empty()) {
        return ""; // no configuration yet
    }
    const metrics::MetricSnapshot &snapshot(
            _manager.getMetricSnapshot(guard, periods[0]));
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
    _manager.updateMetrics(true);
    metrics::MetricLockGuard guard(_manager.getMetricLock());
    _manager.checkMetricsAltered(guard);
    time_t currentTime = time(0);
    std::unique_ptr<metrics::MetricSnapshot> generated(
            new metrics::MetricSnapshot(
                    "Total metrics from start until current time", 0,
                    _manager.getTotalMetricSnapshot(guard).getMetrics(),
                    true));
    _manager.getActiveMetrics(guard)
        .addToSnapshot(*generated, false, currentTime);
    generated->setFromTime(
            _manager.getTotalMetricSnapshot(guard).getFromTime());
    const metrics::MetricSnapshot &snapshot = *generated;
    vespalib::asciistream json;
    vespalib::JsonStream stream(json);
    metrics::JsonWriter metricJsonWriter(stream);
    _manager.visit(guard, snapshot, metricJsonWriter, consumer);
    stream.finalize();
    return json.str();
}

} // namespace metrics
