// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "acceptor.h"
#include <vespa/vespalib/net/socket_spec.h>
#include <functional>
#ifdef __APPLE__
#include <poll.h>
#endif

namespace vespalib::ws {

void
Acceptor::accept_main(Handler<Socket> &socket_handler)
{
#ifdef __APPLE__
    _server_socket.set_blocking(false);
#endif
    while (!_is_closed) {
#ifdef __APPLE__
        pollfd fds;
        fds.fd = _server_socket.get_fd();
        fds.events = POLLIN;
        fds.revents = 0;
        int res = poll(&fds, 1, 10);
        if (res < 1 || fds.revents == 0 || _is_closed) {
            continue;
        }
#endif
        SocketHandle handle = _server_socket.accept();
        if (handle.valid()) {
#ifdef __APPLE__
            handle.set_blocking(true);
#endif
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
