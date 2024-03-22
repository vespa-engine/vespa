// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "producer.h"
#include "metrics_manager.h"
#include "json_formatter.h"
#include "prometheus_formatter.h"

namespace vespalib::metrics {

Producer::Producer(std::shared_ptr<MetricsManager> m)
    : _manager(std::move(m))
{}

Producer::~Producer() = default;

namespace {

vespalib::string
format_snapshot(const Snapshot &snapshot, MetricsProducer::ExpositionFormat format)
{
    switch (format) {
    case MetricsProducer::ExpositionFormat::JSON:
        return JsonFormatter(snapshot).asString();
    case MetricsProducer::ExpositionFormat::Prometheus:
        return PrometheusFormatter(snapshot).as_text_formatted();
    }
    abort();
}

}

vespalib::string
Producer::getMetrics(const vespalib::string &, ExpositionFormat format)
{
    Snapshot snap = _manager->snapshot();
    return format_snapshot(snap, format);
}

vespalib::string
Producer::getTotalMetrics(const vespalib::string &, ExpositionFormat format)
{
    Snapshot snap = _manager->totalSnapshot();
    return format_snapshot(snap, format);
}

} // namespace vespalib::metrics
