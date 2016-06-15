// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/state_server.h>
#include <vespa/vespalib/net/simple_metrics_producer.h>
#include <vespa/vespalib/net/simple_health_producer.h>
#include <vespa/vespalib/net/simple_component_config_producer.h>

namespace filedistribution {

struct StateServerImpl {
    vespalib::SimpleHealthProducer myHealth;
    vespalib::SimpleMetricsProducer myMetrics;
    vespalib::SimpleComponentConfigProducer myComponents;
    vespalib::StateServer myStateServer;

    StateServerImpl(int port) : myStateServer(port, myHealth, myMetrics, myComponents) {}
};

} // namespace filedistribution
