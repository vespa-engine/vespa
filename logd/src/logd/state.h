// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/net/state_server.h>
#include <vespa/vespalib/net/simple_health_producer.h>
#include <vespa/vespalib/net/simple_metrics_producer.h>
#include <vespa/vespalib/net/simple_component_config_producer.h>
#include <vespa/vespalib/net/generic_state_handler.h>

namespace logdemon {

class StateReporter {
    int _port;
    std::unique_ptr<vespalib::StateServer> _server;
    vespalib::SimpleHealthProducer _health;
    vespalib::SimpleMetricsProducer _metrics;
    vespalib::SimpleComponentConfigProducer _components;
public:
    StateReporter();
    void setStatePort(int statePort);
    void gotConf(size_t generation);
};

} // namespace
