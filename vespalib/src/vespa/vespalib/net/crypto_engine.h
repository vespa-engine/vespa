// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "socket_handle.h"
#include "crypto_socket.h"
#include <memory>
#include <mutex>

namespace vespalib {

class SocketSpec;

/**
 * Component responsible for wrapping low-level sockets into
 * appropriate CryptoSocket instances. This is the top-level interface
 * used by code wanting to perform network io with appropriate
 * encryption.
 **/
struct CryptoEngine {
    using SP = std::shared_ptr<CryptoEngine>;
    virtual bool use_tls_when_client() const = 0;
    virtual bool always_use_tls_when_server() const = 0;
    virtual CryptoSocket::UP create_client_crypto_socket(SocketHandle socket, const SocketSpec &spec) = 0;
    virtual CryptoSocket::UP create_server_crypto_socket(SocketHandle socket) = 0;
    virtual ~CryptoEngine();
    static CryptoEngine::SP get_default();
};

/**
 * Crypto engine without encryption.
 **/
struct NullCryptoEngine : public CryptoEngine {
    ~NullCryptoEngine() override;
    bool use_tls_when_client() const override { return false; }
    bool always_use_tls_when_server() const override { return false; }
    CryptoSocket::UP create_client_crypto_socket(SocketHandle socket, const SocketSpec &spec) override;
    CryptoSocket::UP create_server_crypto_socket(SocketHandle socket) override;
};

} // namespace vespalib
