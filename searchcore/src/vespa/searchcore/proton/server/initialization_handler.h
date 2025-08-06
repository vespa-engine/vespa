// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/http/initialization_status_producer.h>
#include <vespa/vespalib/net/http/json_get_handler.h>
#include <vespa/vespalib/net/connection_auth_context.h>
#include <map>
#include <string>

namespace proton {

class InitializationHandler : public vespalib::JsonGetHandler
{
private:
    vespalib::InitializationStatusProducer &_initializationStatusProducer;
public:
    InitializationHandler(vespalib::InitializationStatusProducer &initializationStatusProducer);
    Response get(const std::string &host,
                 const std::string &path,
                 const std::map<std::string,std::string> &params,
                 const vespalib::net::ConnectionAuthContext &auth_ctx) const override;
};

} // namespace proton
