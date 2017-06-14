// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "socket_handle.h"
#include "socket_address.h"

namespace vespalib {

class SocketSpec;

class ServerSocket
{
private:
    SocketHandle _handle;
    vespalib::string _path;

    explicit ServerSocket(SocketHandle handle);
    static ServerSocket listen(const SocketSpec &spec);
    void cleanup();
public:
    ServerSocket() : _handle(), _path() {}
    explicit ServerSocket(const SocketSpec &spec);
    explicit ServerSocket(const vespalib::string &spec);
    explicit ServerSocket(int port);
    ServerSocket(ServerSocket &&rhs);
    ServerSocket &operator=(ServerSocket &&rhs);
    ~ServerSocket() { cleanup(); }
    bool valid() const { return _handle.valid(); }
    int get_fd() const { return _handle.get(); }
    SocketAddress address() const;
    void shutdown();
    bool set_blocking(bool value) { return _handle.set_blocking(value); }
    SocketHandle accept();
};

} // namespace vespalib
