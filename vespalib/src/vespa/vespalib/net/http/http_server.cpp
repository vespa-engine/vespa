// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "http_server.h"
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/net/connection_auth_context.h>

namespace vespalib {

void
HttpServer::get(Portal::GetRequest req)
{
    auto response = _handler_repo.get(req.get_host(), req.get_path(), req.export_params(), req.auth_context());
    if (response.failed()) {
        req.respond_with_error(response.status_code(), response.status_message());
    } else {
        req.respond_with_content("application/json", response.payload());
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
