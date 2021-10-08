// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    typedef std::unique_ptr<StateServer> UP;
    StateServer(int port, const HealthProducer &hp, MetricsProducer &mp, ComponentConfigProducer &ccp);
    ~StateServer();
    int getListenPort() { return _server.port(); }
    JsonHandlerRepo &repo() { return _api.repo(); }
};

} // namespace vespalib
