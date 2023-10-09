// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "socket_handle.h"
#include <memory>

namespace vespalib {

class SocketSpec;

/**
 * Abstract stream-based socket interface.
 **/
struct Socket {
    using UP = std::unique_ptr<Socket>;
    virtual ssize_t read(char *buf, size_t len) = 0;
    virtual ssize_t write(const char *buf, size_t len) = 0;
    virtual ~Socket() {}
};

struct SimpleSocket : public Socket {
    SocketHandle handle;
    explicit SimpleSocket(SocketHandle handle_in) : handle(std::move(handle_in)) {}
    ssize_t read(char *buf, size_t len) final override { return handle.read(buf, len); }
    ssize_t write(const char *buf, size_t len) final override { return handle.write(buf, len); }
    static std::unique_ptr<SimpleSocket> connect(const SocketSpec &spec);
};

} // namespace vespalib
