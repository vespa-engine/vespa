// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include "socket_handle.h"
#include "socket_address.h"
#include "socket.h"

namespace vespalib {

class SocketSpec;

class ServerSocket
{
private:
    SocketHandle _handle;
    vespalib::string _path;

public:
    typedef std::unique_ptr<ServerSocket> UP;
    ServerSocket(const ServerSocket &rhs) = delete;
    ServerSocket &operator=(const ServerSocket &rhs) = delete;
    explicit ServerSocket(SocketHandle handle);
    ~ServerSocket();
    bool valid() const { return _handle.valid(); }
    SocketAddress address() const;
    void shutdown();
    Socket::UP accept();
    static ServerSocket::UP listen(const SocketSpec &spec);
};

} // namespace vespalib
