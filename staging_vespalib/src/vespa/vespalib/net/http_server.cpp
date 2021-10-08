// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "http_server.h"
#include <vespa/vespalib/net/crypto_engine.h>

namespace vespalib {

void
HttpServer::get(Portal::GetRequest req)
{
    vespalib::string json_result = _handler_repo.get(req.get_host(), req.get_path(), req.export_params());
    if (json_result.empty()) {
        req.respond_with_error(404, "Not Found");
    } else {
        req.respond_with_content("application/json", json_result);
    }
}

//-----------------------------------------------------------------------------

HttpServer::HttpServer(int port_in)
    : _handler_repo(),
      _server(Portal::create(CryptoEngine::get_default(), port_in)),
      _root(_server->bind("/", *this))
{
}

HttpServer::~HttpServer()
{
    _root.reset();
}

} // namespace vespalib
