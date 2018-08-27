// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "socket_handle.h"
#include "crypto_socket.h"
#include <memory>
#include <mutex>

namespace vespalib {

/**
 * Component responsible for wrapping low-level sockets into
 * appropriate CryptoSocket instances. This is the top-level interface
 * used by code wanting to perform network io with appropriate
 * encryption.
 **/
struct CryptoEngine {
    using SP = std::shared_ptr<CryptoEngine>;
    virtual CryptoSocket::UP create_crypto_socket(SocketHandle socket, bool is_server) = 0;
    virtual ~CryptoEngine();
    static CryptoEngine::SP get_default();
private:
    static std::mutex _shared_lock;
    static CryptoEngine::SP _shared_default;
};

/**
 * Crypto engine without encryption.
 **/
struct NullCryptoEngine : public CryptoEngine {
    CryptoSocket::UP create_crypto_socket(SocketHandle socket, bool is_server) override;
};

/**
 * Very simple crypto engine that requires connection handshaking and
 * data transformation. Used to test encryption integration separate
 * from TLS.
 **/
struct XorCryptoEngine : public CryptoEngine {
    CryptoSocket::UP create_crypto_socket(SocketHandle socket, bool is_server) override;
};

} // namespace vespalib
