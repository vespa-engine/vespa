// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "state_reporter.h"
#include <vespa/vespalib/metrics/simple_metrics_manager.h>

#include <vespa/log/log.h>
LOG_SETUP("");

namespace logdemon {

using vespalib::metrics::SimpleMetricsManager;
using vespalib::metrics::SimpleManagerConfig;

StateReporter::StateReporter()
    : _port(-1),
      _health(),
      _components(),
      _metrics(SimpleMetricsManager::create(SimpleManagerConfig())),
      _producer(_metrics),
      _server()
{
}

StateReporter::~StateReporter() = default;

void
StateReporter::setStatePort(int statePort)
{
    if (statePort != _port) {
        _port = statePort;
        _server.reset(new vespalib::StateServer(_port, _health, _producer, _components));
        LOG(info, "state server listening on port %d", _server->getListenPort());
    }
}

void
StateReporter::gotConf(size_t generation)
{
    vespalib::ComponentConfigProducer::Config conf("logd", generation);
    _components.addConfig(conf);
}

} // namespace
