// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "producer.h"
#include "metrics_manager.h"
#include "json_formatter.h"

namespace vespalib::metrics {

Producer::Producer(std::shared_ptr<MetricsManager> m)
    : _manager(std::move(m))
{}

Producer::~Producer() = default;

vespalib::string
Producer::getMetrics(const vespalib::string &, ExpositionFormat /*ignored*/)
{
    Snapshot snap = _manager->snapshot();
    JsonFormatter fmt(snap);
    return fmt.asString();
}

vespalib::string
Producer::getTotalMetrics(const vespalib::string &, ExpositionFormat /*ignored*/)
{
    Snapshot snap = _manager->totalSnapshot();
    JsonFormatter fmt(snap);
    return fmt.asString();
}

} // namespace vespalib::metrics
