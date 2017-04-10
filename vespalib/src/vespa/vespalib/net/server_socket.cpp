// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "server_socket.h"
#include "socket_spec.h"

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.net.server_socket");

namespace vespalib {

bool is_socket(const vespalib::string &path) {
    struct stat info;
    if (path.empty() || (lstat(path.c_str(), &info) != 0)) {
        return false;
    }
    return S_ISSOCK(info.st_mode);
}

ServerSocket::ServerSocket(SocketHandle handle)
    : _handle(std::move(handle)),
      _path(SocketAddress::address_of(_handle.get()).path())
{
}

ServerSocket::~ServerSocket()
{
    if (is_socket(_path)) {
        unlink(_path.c_str());
    }
}

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
    if (!handle.valid() && is_socket(spec.path())) {
        if (!spec.client_address().connect().valid()) {
            LOG(warning, "removing old socket: '%s'", spec.path().c_str());
            unlink(spec.path().c_str());
            handle = spec.server_address().listen();
        }
    }
    return std::make_unique<ServerSocket>(std::move(handle));
}

} // namespace vespalib
