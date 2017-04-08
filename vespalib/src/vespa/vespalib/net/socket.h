// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <vespa/vespalib/stllike/string.h>
#include "socket_handle.h"
#include "socket_address.h"

namespace vespalib {

class SocketSpec;

class Socket
{
private:
    SocketHandle _handle;

public:
    typedef std::unique_ptr<Socket> UP;
    Socket(const Socket &rhs) = delete;
    Socket &operator=(const Socket &rhs) = delete;
    explicit Socket(SocketHandle handle) : _handle(std::move(handle)) {}
    bool valid() const { return _handle.valid(); }
    SocketAddress address() const;
    SocketAddress peer_address() const;
    void shutdown();
    ssize_t read(char *buf, size_t len);
    ssize_t write(const char *buf, size_t len);
    static Socket::UP connect(const SocketSpec &spec);
};

} // namespace vespalib
