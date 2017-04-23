// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "acceptor.h"
#include <functional>
#include <vespa/vespalib/net/socket_spec.h>

namespace vespalib {
namespace ws {

void
Acceptor::accept_main(Handler<Socket> &socket_handler)
{
    while (!_is_closed) {
        SocketHandle handle = _server_socket.accept();
        if (handle.valid()) {
            socket_handler.handle(std::make_unique<SimpleSocket>(std::move(handle)));
        }
    }
}

Acceptor::Acceptor(int port_in, Handler<Socket> &socket_handler)
    : _server_socket(port_in),
      _is_closed(false),
      _accept_thread(&Acceptor::accept_main, this, std::ref(socket_handler))
{
}

Acceptor::~Acceptor()
{
    _server_socket.shutdown();
    _is_closed = true;
    _accept_thread.join();
}

} // namespace vespalib::ws
} // namespace vespalib
