// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "crypto_engine.h"
#include <vespa/vespalib/crypto/crypto_exception.h>
#include <vespa/vespalib/net/tls/authorization_mode.h>
#include <vespa/vespalib/net/tls/auto_reloading_tls_crypto_engine.h>
#include <vespa/vespalib/net/tls/maybe_tls_crypto_engine.h>
#include <vespa/vespalib/net/tls/statistics.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.net.crypto_engine");

namespace vespalib {

namespace {

class NullCryptoSocket : public CryptoSocket
{
private:
    SocketHandle _socket;
public:
    explicit NullCryptoSocket(SocketHandle socket) : _socket(std::move(socket)) {}
    ~NullCryptoSocket() override;
    [[nodiscard]] int get_fd() const override { return _socket.get(); }
    HandshakeResult handshake() override { return HandshakeResult::DONE; }
    void do_handshake_work() override {}
    [[nodiscard]] size_t min_read_buffer_size() const override { return 1; }
    ssize_t read(char *buf, size_t len) override { return _socket.read(buf, len); }
    ssize_t drain(char *, size_t) override { return 0; }
    ssize_t write(const char *buf, size_t len) override { return _socket.write(buf, len); }
    ssize_t flush() override { return 0; }
    ssize_t half_close() override { return _socket.half_close(); }
    void drop_empty_buffers() override {}
};

    NullCryptoSocket::~NullCryptoSocket() = default;
using net::tls::AuthorizationMode;

AuthorizationMode
authorization_mode_from_env() {
    const char* env = getenv("VESPA_TLS_INSECURE_AUTHORIZATION_MODE");
    vespalib::string mode = env ? env : "";
    if (mode == "enforce") {
        return AuthorizationMode::Enforce;
    } else if (mode == "log_only") {
        return AuthorizationMode::LogOnly;
    } else if (mode == "disable") {
        return AuthorizationMode::Disable;
    } else if (!mode.empty()) {
        LOG(warning, "VESPA_TLS_INSECURE_AUTHORIZATION_MODE environment variable has "
                     "an unsupported value (%s). Falling back to 'enforce'", mode.c_str());
    }
    return AuthorizationMode::Enforce;
}

CryptoEngine::SP
create_default_crypto_engine() {
    const char *env = getenv("VESPA_TLS_CONFIG_FILE");
    vespalib::string cfg_file = env ? env : "";
    if (cfg_file.empty()) {
        return std::make_shared<NullCryptoEngine>();
    }
    auto mode = authorization_mode_from_env();
    LOG(debug, "Using TLS crypto engine with config file '%s'", cfg_file.c_str());
    auto tls = std::make_shared<net::tls::AutoReloadingTlsCryptoEngine>(cfg_file, mode);
    env = getenv("VESPA_TLS_INSECURE_MIXED_MODE");
    vespalib::string mixed_mode = env ? env : "";
    if (mixed_mode == "plaintext_client_mixed_server") {
        LOG(debug, "TLS insecure mixed-mode activated: plaintext client, mixed server");
        return std::make_shared<MaybeTlsCryptoEngine>(std::move(tls), false);
    } else if (mixed_mode == "tls_client_mixed_server") {
        LOG(debug, "TLS insecure mixed-mode activated: TLS client, mixed server");
        return std::make_shared<MaybeTlsCryptoEngine>(std::move(tls), true);
    } else if (!mixed_mode.empty() && (mixed_mode != "tls_client_tls_server")) {
        LOG(warning, "bad TLS insecure mixed-mode specified: '%s' (ignoring)",
            mixed_mode.c_str());
    }
    return tls;
}

CryptoEngine::SP
try_create_default_crypto_engine() {
    try {
        return create_default_crypto_engine();
    } catch (crypto::CryptoException &e) {
        LOG(error, "failed to create default crypto engine: %s", e.what());
        std::_Exit(78);
    }
}

} // namespace vespalib::<unnamed>

CryptoEngine::~CryptoEngine() = default;

CryptoEngine::SP
CryptoEngine::get_default()
{
    static const CryptoEngine::SP shared_default = try_create_default_crypto_engine();
    return shared_default;
}

NullCryptoEngine::~NullCryptoEngine() = default;

CryptoSocket::UP
NullCryptoEngine::create_client_crypto_socket(SocketHandle socket, const SocketSpec &)
{
    net::tls::ConnectionStatistics::get(false).inc_insecure_connections();
    return std::make_unique<NullCryptoSocket>(std::move(socket));
}

CryptoSocket::UP
NullCryptoEngine::create_server_crypto_socket(SocketHandle socket)
{
    net::tls::ConnectionStatistics::get(true).inc_insecure_connections();
    return std::make_unique<NullCryptoSocket>(std::move(socket));
}

} // namespace vespalib
