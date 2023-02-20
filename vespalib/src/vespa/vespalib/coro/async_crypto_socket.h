// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "lazy.h"
#include "async_io.h"

#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/net/socket_handle.h>
#include <vespa/vespalib/net/crypto_engine.h>

#include <memory>

namespace vespalib::coro {

// A socket endpoint supporting async read/write with encryption

struct AsyncCryptoSocket {
    using UP = std::unique_ptr<AsyncCryptoSocket>;

    virtual Lazy<ssize_t> read(char *buf, size_t len) = 0;
    virtual Lazy<ssize_t> write(const char *buf, size_t len) = 0;
    virtual ~AsyncCryptoSocket();

    static Lazy<AsyncCryptoSocket::UP> accept(AsyncIo &async, CryptoEngine &crypto,
                                              SocketHandle handle);
    static Lazy<AsyncCryptoSocket::UP> connect(AsyncIo &async, CryptoEngine &crypto,
                                               SocketHandle handle, SocketSpec spec);
};

}
