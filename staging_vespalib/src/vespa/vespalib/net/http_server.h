// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastlib/net/httpserver.h>
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
class HttpServer
{
private:
    struct Server : Fast_HTTPServer {
        typedef std::unique_ptr<Server> UP;
        const HttpServer &parent;
        virtual void onGetRequest(const vespalib::string &url, const vespalib::string &host,
                                  Fast_HTTPConnection &conn) override
        {
            parent.handle_get(url, host, conn);
        }
        Server(int port, const HttpServer &parent_in) : Fast_HTTPServer(port), parent(parent_in) {}
    };

    int _requested_port;
    volatile bool _started;
    int _actual_port;
    vespalib::string _my_host;
    JsonHandlerRepo _handler_repo;
    Server::UP _server; // need separate object for controlled shutdown

    void handle_get(const vespalib::string &url, const vespalib::string &host,
                    Fast_HTTPConnection &conn) const;
public:
    typedef std::unique_ptr<HttpServer> UP;
    HttpServer(int port_in);
    ~HttpServer();
    const vespalib::string &host() const { return _my_host; }
    JsonHandlerRepo &repo() { return _handler_repo; }
    void start();
    int port() const { return (_actual_port != 0) ? _actual_port : _requested_port; }
    void stop();
};

} // namespace vespalib
