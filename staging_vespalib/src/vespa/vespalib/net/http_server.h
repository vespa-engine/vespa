// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/portal/portal.h>
#include "json_handler_repo.h"

namespace vespalib {

/**
 * A simple HTTP server that can be used to handle GET requests
 * returning json (typically simple read-only REST APIs). Either pass
 * a specific port to the constructor or use 0 to bind to a random
 * port. Note that you may not ask about the actual port until after
 * the server has been started. Request dispatching is done using a
 * JsonHandlerRepo.
 **/
class HttpServer : public Portal::GetHandler
{
private:
    JsonHandlerRepo _handler_repo;
    Portal::SP _server;
    Portal::Token::UP _root;

    void get(Portal::GetRequest req) override;
public:
    typedef std::unique_ptr<HttpServer> UP;
    HttpServer(int port_in);
    ~HttpServer();
    const vespalib::string &host() const { return _server->my_host(); }
    JsonHandlerRepo &repo() { return _handler_repo; }
    int port() const { return _server->listen_port(); }
};

} // namespace vespalib
