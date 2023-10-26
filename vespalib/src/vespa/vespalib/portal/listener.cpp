// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "listener.h"
#include <vespa/vespalib/util/exceptions.h>
#include <cassert>

namespace vespalib::portal {

Listener::Listener(Reactor &reactor, int port, std::function<void(SocketHandle)> handler)
    : _server_socket(port),
      _handler(std::move(handler)),
      _token()
{
    if (_server_socket.valid()) {
        bool async = _server_socket.set_blocking(false);
        assert(async);
        _token = reactor.attach(*this, _server_socket.get_fd(), true, false);
    } else {
        throw PortListenException(port, "PORTAL");
    }
}

Listener::~Listener()
{
    _token.reset();
}

void
Listener::handle_event(bool, bool)
{
    SocketHandle handle = _server_socket.accept();
    if (handle.valid()) {
        _handler(std::move(handle));
    }
}

} // namespace vespalib::portal
