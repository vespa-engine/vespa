// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "jsonwriter.h"
#include "metricmanager.h"
#include "prometheus_writer.h"
#include "state_api_adapter.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace metrics {

vespalib::string
StateApiAdapter::getMetrics(const vespalib::string &consumer, ExpositionFormat format)
{
    MetricLockGuard guard(_manager.getMetricLock());
    auto periods = _manager.getSnapshotPeriods(guard);
    if (periods.empty() || !_manager.any_snapshots_taken(guard)) {
        return ""; // no configuration or snapshots yet
    }
    const MetricSnapshot& snapshot(_manager.getMetricSnapshot(guard, periods[0]));
    vespalib::asciistream out;
    // Using `switch` instead of `if` so that we fail with a compiler warning -> error if
    // we add another enum value and forget to add a case for it here.
    switch (format) {
    case ExpositionFormat::JSON:
        {
            vespalib::JsonStream stream(out);
            JsonWriter metricJsonWriter(stream);
            _manager.visit(guard, snapshot, metricJsonWriter, consumer);
            stream.finalize();
            break;
        }
    case ExpositionFormat::Prometheus:
        {
            PrometheusWriter writer(out);
            _manager.visit(guard, snapshot, writer, consumer);
            break;
        }
    }
    return out.str();
}

vespalib::string
StateApiAdapter::getTotalMetrics(const vespalib::string &consumer, ExpositionFormat format)
{
    _manager.updateMetrics();
    MetricLockGuard guard(_manager.getMetricLock());
    _manager.checkMetricsAltered(guard);
    system_time currentTime = vespalib::system_clock::now();
    auto generated = std::make_unique<MetricSnapshot>("Total metrics from start until current time", 0s,
                                                      _manager.getTotalMetricSnapshot(guard).getMetrics(), true);
    _manager.getActiveMetrics(guard).addToSnapshot(*generated, false, currentTime);
    generated->setFromTime(_manager.getTotalMetricSnapshot(guard).getFromTime());
    const MetricSnapshot& snapshot = *generated;
    vespalib::asciistream out;
    switch (format) {
    case ExpositionFormat::JSON:
        {
            vespalib::JsonStream stream(out);
            metrics::JsonWriter metricJsonWriter(stream);
            _manager.visit(guard, snapshot, metricJsonWriter, consumer);
            stream.finalize();
            break;
        }
    case ExpositionFormat::Prometheus:
        {
            PrometheusWriter writer(out);
            _manager.visit(guard, snapshot, writer, consumer);
            break;
        }
    }

    return out.str();
}

} // namespace metrics
