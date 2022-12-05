// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "lazy.h"

#include <memory>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/net/socket_handle.h>
#include <vespa/vespalib/net/server_socket.h>

namespace vespalib::coro {

// Interfaces defining functions used to perform async io. The initial
// implementation will perform epoll in a single dedicated thread. The
// idea is to be able to switch to an implementation using io_uring
// some time in the future without having to change existing client
// code.

struct AsyncIo : std::enable_shared_from_this<AsyncIo> {
    // these objects should not be copied around
    AsyncIo(const AsyncIo &) = delete;
    AsyncIo(AsyncIo &&) = delete;
    AsyncIo &operator=(const AsyncIo &) = delete;
    AsyncIo &operator=(AsyncIo &&) = delete;
    virtual ~AsyncIo();

    // create an async_io 'runtime' with the default implementation
    static std::shared_ptr<AsyncIo> create();

    // implementation tag
    virtual vespalib::string get_impl_spec() = 0;
    
    // api for async io used by coroutines
    virtual Lazy<SocketHandle> accept(ServerSocket &server_socket) = 0;
    virtual Lazy<SocketHandle> connect(const SocketAddress &addr) = 0;
    virtual Lazy<ssize_t> read(SocketHandle &handle, char *buf, size_t len) = 0;
    virtual Lazy<ssize_t> write(SocketHandle &handle, const char *buf, size_t len) = 0;
    virtual Work schedule() = 0;

protected:
    // may only be created via subclass
    AsyncIo();
};

}
