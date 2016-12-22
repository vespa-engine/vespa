// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "socket.h"

namespace vespalib {

SocketAddress
Socket::address() const
{
    return SocketAddress::address_of(_handle.get());
}

SocketAddress
Socket::peer_address() const
{
    return SocketAddress::peer_address(_handle.get());
}

void
Socket::shutdown()
{
    if (valid()) {
        ::shutdown(_handle.get(), SHUT_RDWR);
    }
}

ssize_t
Socket::read(char *buf, size_t len)
{
    for (;;) {
        ssize_t result = ::read(_handle.get(), buf, len);
        if ((result >= 0) || (errno != EINTR)) {
            return result;
        }
    }
}

ssize_t
Socket::write(const char *buf, size_t len)
{
    for (;;) {
        ssize_t result = ::write(_handle.get(), buf, len);
        if ((result >= 0) || (errno != EINTR)) {
            return result;
        }
    }
}

Socket::UP
Socket::connect(const vespalib::string &host, int port)
{
    SocketHandle handle = SocketAddress::select_remote(port, host.c_str()).connect();
    return std::make_unique<Socket>(std::move(handle));
}

Socket::UP
Socket::connect(const vespalib::string &path)
{
    SocketHandle handle = SocketAddress::from_path(path).connect();
    return std::make_unique<Socket>(std::move(handle));
}

} // namespace vespalib
