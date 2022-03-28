// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "socket_handle.h"
#include "socket_address.h"
#include <atomic>

namespace vespalib {

class SocketSpec;

class ServerSocket
{
private:
    SocketHandle _handle;
    vespalib::string _path;
    bool _blocking;
    std::atomic<bool> _shutdown;

    void cleanup();
public:
    ServerSocket() : _handle(), _path(), _blocking(false), _shutdown(false) {}
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
    bool set_blocking(bool value) {
        _blocking = value;
        return true;
    }
    SocketHandle accept();
};

} // namespace vespalib
