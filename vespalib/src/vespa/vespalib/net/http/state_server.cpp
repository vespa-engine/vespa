// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "state_server.h"

namespace vespalib {

StateServer::StateServer(int port,
                         const HealthProducer &hp,
                         MetricsProducer &mp,
                         ComponentConfigProducer &ccp,
                         bool limit_endpoints)
    : _api(hp, mp, ccp, limit_endpoints),
      _server(port),
      _tokens()
{
    _tokens.push_back(_server.repo().bind("/state/v1", _api));
    _tokens.push_back(_server.repo().bind("/metrics/total", _api));
}

StateServer::~StateServer() = default;

} // namespace vespalib
