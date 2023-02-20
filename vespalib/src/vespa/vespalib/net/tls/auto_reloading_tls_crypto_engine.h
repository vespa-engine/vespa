// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/tls/authorization_mode.h>
#include <vespa/vespalib/net/tls/tls_crypto_engine.h>
#include <vespa/vespalib/stllike/string.h>

#include <chrono>
#include <condition_variable>
#include <mutex>
#include <thread>

namespace vespalib::net::tls {

class AutoReloadingTlsCryptoEngine : public AbstractTlsCryptoEngine {
public:
    using EngineSP     = std::shared_ptr<TlsCryptoEngine>;
    using TimeInterval = std::chrono::steady_clock::duration;
private:
    AuthorizationMode       _authorization_mode;
    mutable std::mutex      _thread_mutex;
    std::condition_variable _thread_cond;
    mutable std::mutex      _engine_mutex;
    bool                    _shutdown;
    const vespalib::string  _config_file_path;
    EngineSP                _current_engine; // Access must be under _engine_mutex
    TimeInterval            _reload_interval;
    std::thread             _reload_thread;

    void run_reload_loop();
    void try_replace_current_engine();
    std::chrono::steady_clock::time_point make_future_reload_time_point() const noexcept;

public:
    explicit AutoReloadingTlsCryptoEngine(vespalib::string config_file_path,
                                          AuthorizationMode mode,
                                          TimeInterval reload_interval = std::chrono::seconds(3600));
    ~AutoReloadingTlsCryptoEngine() override;

    AutoReloadingTlsCryptoEngine(const AutoReloadingTlsCryptoEngine&) = delete;
    AutoReloadingTlsCryptoEngine& operator=(const AutoReloadingTlsCryptoEngine&) = delete;
    AutoReloadingTlsCryptoEngine(AutoReloadingTlsCryptoEngine&&) = delete;
    AutoReloadingTlsCryptoEngine& operator=(AutoReloadingTlsCryptoEngine&&) = delete;

    EngineSP acquire_current_engine() const;

    CryptoSocket::UP create_client_crypto_socket(SocketHandle socket, const SocketSpec &spec) override;
    CryptoSocket::UP create_server_crypto_socket(SocketHandle socket) override;
    bool use_tls_when_client() const override;
    bool always_use_tls_when_server() const override;
    std::unique_ptr<CryptoCodec> create_tls_client_crypto_codec(const SocketHandle &socket, const SocketSpec &spec) override;
    std::unique_ptr<CryptoCodec> create_tls_server_crypto_codec(const SocketHandle &socket) override;
};

}
