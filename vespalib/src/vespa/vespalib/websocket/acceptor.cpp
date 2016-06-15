// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "acceptor.h"
#include <functional>

namespace vespalib {
namespace ws {

void
Acceptor::accept_main(Handler<Socket> &socket_handler)
{
    while (!_server_socket.is_closed()) {
        Socket::UP socket = _server_socket.accept();
        if (socket) {
            socket_handler.handle(std::move(socket));
        }
    }
}

Acceptor::Acceptor(int port_in, Handler<Socket> &socket_handler)
    : _server_socket(port_in),
      _accept_thread(&Acceptor::accept_main, this, std::ref(socket_handler))
{
}

Acceptor::~Acceptor()
{
    _server_socket.close();
    _accept_thread.join();
}

} // namespace vespalib::ws
} // namespace vespalib
