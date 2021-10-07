// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "producer.h"
#include "metrics_manager.h"
#include "json_formatter.h"

namespace vespalib {
namespace metrics {

Producer::Producer(std::shared_ptr<MetricsManager> m)
    : _manager(m)
{}

vespalib::string
Producer::getMetrics(const vespalib::string &)
{
    Snapshot snap = _manager->snapshot();
    JsonFormatter fmt(snap);
    return fmt.asString();
}

vespalib::string
Producer::getTotalMetrics(const vespalib::string &)
{
    Snapshot snap = _manager->totalSnapshot();
    JsonFormatter fmt(snap);
    return fmt.asString();
}



} // namespace vespalib::metrics
} // namespace vespalib
