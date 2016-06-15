// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/util/exceptions.h>
#include <sys/socket.h>
#include "server_socket.h"

namespace vespalib {
namespace ws {

ServerSocket::ServerSocket(int port_in)
    : _server_socket(port_in),
      _closed(false)
{
    _server_socket.SetSoBlocking(true);
    if (!_server_socket.Listen()) {
        throw PortListenException(port_in, "tcp");
    }
}

Socket::UP
ServerSocket::accept()
{
    std::unique_ptr<FastOS_Socket> socket(_server_socket.AcceptPlain());
    if (!socket) {
        return Socket::UP();
    }
    socket->SetSoBlocking(true);
    return Socket::UP(new Socket(std::move(socket)));
}

void
ServerSocket::close()
{
    _closed = true;
    int fd = _server_socket.get_fd();
    if (fd >= 0) {
        shutdown(fd, SHUT_RDWR);
    }
}

} // namespace vespalib::ws
} // namespace vespalib
