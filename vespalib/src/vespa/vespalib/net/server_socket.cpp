// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "server_socket.h"
#include "socket_spec.h"

namespace vespalib {

SocketAddress
ServerSocket::address() const
{
    return SocketAddress::address_of(_handle.get());
}

void
ServerSocket::shutdown()
{
    if (valid()) {
        ::shutdown(_handle.get(), SHUT_RDWR);
    }
}

Socket::UP
ServerSocket::accept()
{
    SocketHandle handle(::accept(_handle.get(), nullptr, 0));
    return std::make_unique<Socket>(std::move(handle));
}

ServerSocket::UP
ServerSocket::listen(const SocketSpec &spec)
{
    SocketHandle handle = spec.server_address().listen();
    return std::make_unique<ServerSocket>(std::move(handle));
}

} // namespace vespalib
