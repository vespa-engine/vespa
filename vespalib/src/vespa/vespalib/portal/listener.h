// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "reactor.h"
#include <vespa/vespalib/net/server_socket.h>
#include <functional>

namespace vespalib::portal {

class Listener : public Reactor::EventHandler
{
private:
    ServerSocket _server_socket;
    std::function<void(SocketHandle)> _handler;
    Reactor::Token::UP _token;
public:
    using UP = std::unique_ptr<Listener>;
    Listener(Reactor &reactor, int port, std::function<void(SocketHandle)> handler);
    ~Listener();
    int listen_port() const { return _server_socket.address().port(); }
    void handle_event(bool read, bool write) override;
};

} // namespace vespalib::portal
