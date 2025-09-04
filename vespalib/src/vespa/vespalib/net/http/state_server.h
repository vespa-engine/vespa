// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "http_server.h"
#include "state_api.h"
#include "json_get_handler.h"
#include "health_producer.h"
#include "metrics_producer.h"
#include "component_config_producer.h"
#include "json_handler_repo.h"

namespace vespalib {

/**
 * An all-in-one server making it simple for applications to serve the
 * 'state' REST API over HTTP.
 **/
class StateServer
{
private:
    StateApi _api;
    HttpServer _server;
    std::vector<JsonHandlerRepo::Token::UP> _tokens;

public:
    using UP = std::unique_ptr<StateServer>;
    StateServer(int port, const HealthProducer &hp, MetricsProducer &mp, ComponentConfigProducer &ccp, bool limitEndpoints = false);
    ~StateServer();
    int getListenPort() { return _server.port(); }
    JsonHandlerRepo &repo() { return _api.repo(); }
    void setLimitEndpoints(bool limitEndpoints) { _api.setLimitEndpoints(limitEndpoints); }
};

} // namespace vespalib
