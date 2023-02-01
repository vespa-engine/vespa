// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "lazy.h"

#include <memory>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/net/socket_handle.h>
#include <vespa/vespalib/net/server_socket.h>

namespace vespalib::coro {

// Interface defining functions used to perform async io. The initial
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
    using SP = std::shared_ptr<AsyncIo>;

    // tag used to separate implementations
    enum class ImplTag { EPOLL, URING };
    static constexpr ImplTag default_impl() { return ImplTag::EPOLL; }

    // thin wrapper used by the owner to handle lifetime
    class Owner {
    private:
        std::shared_ptr<AsyncIo> _async_io;
        bool _init_shutdown_called;
        bool _fini_shutdown_called;
    public:
        Owner(std::shared_ptr<AsyncIo> async_io);
        Owner(const Owner &) = delete;
        Owner &operator=(const Owner &) = delete;
        Owner(Owner &&) = default;
        Owner &operator=(Owner &&) = default;
        AsyncIo::SP share() { return _async_io->shared_from_this(); }
        operator AsyncIo &() { return *_async_io; }
        void init_shutdown();
        void fini_shutdown();
        ~Owner();
    };

    // create an async_io 'runtime'
    // note that the preferred implementation may not be available
    static Owner create(ImplTag prefer_impl = default_impl());

    // implementation tag
    virtual ImplTag get_impl_tag() = 0;

    // api for async io used by coroutines
    virtual Lazy<SocketHandle> accept(ServerSocket &server_socket) = 0;
    virtual Lazy<SocketHandle> connect(const SocketAddress &addr) = 0;
    virtual Lazy<ssize_t> read(SocketHandle &handle, char *buf, size_t len) = 0;
    virtual Lazy<ssize_t> write(SocketHandle &handle, const char *buf, size_t len) = 0;
    virtual Lazy<bool> schedule() = 0;

protected:
    // may only be created via subclass
    AsyncIo();

private:
    // called by Owner
    virtual void start() = 0;
    virtual void init_shutdown() = 0;
    virtual void fini_shutdown() = 0;
};

}
