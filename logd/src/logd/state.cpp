// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP("");

#include "state.h"

namespace logdemon {

StateReporter::StateReporter()
{
}

void
StateReporter::setStatePort(int statePort)
{
    if (statePort != _port) {
        _port = statePort;
        _server.reset(new vespalib::StateServer(_port, _health, _metrics, _components));
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
