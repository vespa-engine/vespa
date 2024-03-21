// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "rpchooks.h"
#include <vespa/vespalib/net/http/metrics_producer.h>
#include <vespa/vespalib/net/http/simple_metrics_producer.h>
#include <chrono>

class FNET_Transport;

namespace slobrok {

class MetricsProducer : public vespalib::MetricsProducer
{
private:
    const RPCHooks &_rpcHooks;
    RPCHooks::Metrics _lastMetrics;
    vespalib::SimpleMetricsProducer _producer;
    std::chrono::system_clock::time_point _startTime;
    std::chrono::system_clock::time_point _lastSnapshotStart;
    std::unique_ptr<FNET_Task> _snapshotter;

public:
    vespalib::string getMetrics(const vespalib::string &consumer, ExpositionFormat format) override;
    vespalib::string getTotalMetrics(const vespalib::string &consumer, ExpositionFormat format) override;

    void snapshot();

    MetricsProducer(const RPCHooks &hooks, FNET_Transport &transport);
    ~MetricsProducer() override;
};


} // namespace slobrok

