// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reconfigurable_stateserver.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/net/state_server.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".reconfigurable_stateserver");

using namespace std::chrono_literals;

namespace slobrok {

ReconfigurableStateServer::ReconfigurableStateServer(const config::ConfigUri & configUri,
                                                     vespalib::HealthProducer & health,
                                                     vespalib::MetricsProducer & metrics,
                                                     vespalib::ComponentConfigProducer & components)
    : _health(health),
      _metrics(metrics),
      _components(components),
      _configFetcher(std::make_unique<config::ConfigFetcher>(configUri.getContext())),
      _server()
{
    _configFetcher->subscribe<vespa::config::StateserverConfig>(configUri.getConfigId(), this);
    _configFetcher->start();
}

ReconfigurableStateServer::~ReconfigurableStateServer() = default;

void
ReconfigurableStateServer::configure(std::unique_ptr<vespa::config::StateserverConfig> config)
{
    _server.reset();
    for (size_t retryTime(1); !_server && (retryTime < 10); retryTime++) {
        try {
            _server = std::make_unique<vespalib::StateServer>(config->httpport, _health, _metrics, _components);
        } catch (vespalib::PortListenException & e) {
            LOG(warning, "Failed listening to network port(%d) with protocol(%s): '%s', will retry for 60s",
                e.get_port(), e.get_protocol().c_str(), e.what());
            std::this_thread::sleep_for(retryTime * 1s);
        }
    }
}

}
