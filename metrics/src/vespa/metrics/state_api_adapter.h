// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/http/metrics_producer.h>

namespace metrics {

class MetricManager;

/**
 * This is an adapter class that implements the metrics producer
 * interface defined by the state api implementation in vespalib by
 * extracting metrics in json format from a metric manager.
 **/
class StateApiAdapter : public vespalib::MetricsProducer
{
private:
    MetricManager &_manager;

public:
    StateApiAdapter(MetricManager &manager) : _manager(manager) {}

    vespalib::string getMetrics(const vespalib::string &consumer) override;
    vespalib::string getTotalMetrics(const vespalib::string &consumer) override;
};

} // namespace metrics
