// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "json_get_handler.h"
#include "health_producer.h"
#include "metrics_producer.h"
#include "component_config_producer.h"
#include <memory>
#include "json_handler_repo.h"

namespace vespalib {

/**
 * This class uses the underlying producer interfaces passed to the
 * constructor to implement the 'state' REST API. The get function is
 * a simple abstraction of a GET request returning json and can be
 * wired into the HttpServer or called directly.
 **/
class StateApi : public JsonGetHandler
{
private:
    const HealthProducer &_healthProducer;
    MetricsProducer &_metricsProducer;
    ComponentConfigProducer &_componentConfigProducer;
    JsonHandlerRepo _handler_repo;

public:
    StateApi(const HealthProducer &hp,
             MetricsProducer &mp,
             ComponentConfigProducer &ccp);
    ~StateApi() override;
    Response get(const vespalib::string &host,
                 const vespalib::string &path,
                 const std::map<vespalib::string,vespalib::string> &params,
                 const net::ConnectionAuthContext &auth_ctx) const override;
    JsonHandlerRepo &repo() { return _handler_repo; }
};

} // namespace vespalib
