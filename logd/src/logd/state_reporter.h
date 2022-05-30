// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/net/http/state_server.h>
#include <vespa/vespalib/net/http/simple_health_producer.h>
#include <vespa/vespalib/net/http/simple_component_config_producer.h>
#include <vespa/vespalib/net/http/generic_state_handler.h>
#include <vespa/vespalib/metrics/metrics_manager.h>
#include <vespa/vespalib/metrics/producer.h>

namespace logdemon {

/**
 * Class used to serve /state/v1 REST API over HTTP for vespa-logd process.
 */
class StateReporter {
    int _port;
    vespalib::SimpleHealthProducer _health;
    vespalib::SimpleComponentConfigProducer _components;
    std::shared_ptr<vespalib::metrics::MetricsManager> _metrics;
    vespalib::metrics::Producer _producer;
    std::unique_ptr<vespalib::StateServer> _server;
public:
    StateReporter();
    ~StateReporter();
    void setStatePort(int statePort);
    void gotConf(size_t generation);
    std::shared_ptr<vespalib::metrics::MetricsManager> metrics() { return _metrics; }
};

} // namespace
