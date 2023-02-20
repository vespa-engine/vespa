// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "auto_reloading_tls_crypto_engine.h"
#include "statistics.h"
#include "tls_context.h"
#include "tls_crypto_engine.h"
#include "transport_security_options.h"
#include "transport_security_options_reading.h"
#include "crypto_codec.h"

#include <functional>
#include <stdexcept>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.net.tls.auto_reloading_tls_crypto_engine");

namespace vespalib::net::tls {

namespace {

std::shared_ptr<TlsCryptoEngine> tls_engine_from_config_file(const vespalib::string& config_file_path,
                                                             AuthorizationMode authz_mode) {
    auto tls_opts = net::tls::read_options_from_json_file(config_file_path);
    return std::make_shared<TlsCryptoEngine>(*tls_opts, authz_mode);
}

std::shared_ptr<TlsCryptoEngine> try_create_engine_from_tls_config(const vespalib::string& config_file_path,
                                                                   AuthorizationMode authz_mode) {
    try {
        return tls_engine_from_config_file(config_file_path, authz_mode);
    } catch (std::exception& e) {
        LOG(warning, "Failed to reload TLS config file (%s): '%s'. Old config remains in effect.",
            config_file_path.c_str(), e.what());
        ConfigStatistics::get().inc_failed_config_reloads();
        return {};
    }
}

} // anonymous namespace

AutoReloadingTlsCryptoEngine::AutoReloadingTlsCryptoEngine(vespalib::string config_file_path,
                                                           AuthorizationMode mode,
                                                           TimeInterval reload_interval)
    : _authorization_mode(mode),
      _thread_mutex(),
      _thread_cond(),
      _engine_mutex(),
      _shutdown(false),
      _config_file_path(std::move(config_file_path)),
      _current_engine(tls_engine_from_config_file(_config_file_path, _authorization_mode)),
      _reload_interval(reload_interval),
      _reload_thread([this](){ run_reload_loop(); })
{
}

AutoReloadingTlsCryptoEngine::~AutoReloadingTlsCryptoEngine() {
    {
        std::lock_guard lock(_thread_mutex);
        _shutdown = true;
        _thread_cond.notify_all();
    }
    _reload_thread.join();
}

std::chrono::steady_clock::time_point AutoReloadingTlsCryptoEngine::make_future_reload_time_point() const noexcept {
    return std::chrono::steady_clock::now() + _reload_interval;
}

void AutoReloadingTlsCryptoEngine::run_reload_loop() {
    std::unique_lock lock(_thread_mutex);
    auto reload_at_time = make_future_reload_time_point();
    while (!_shutdown) {
        if (_thread_cond.wait_until(lock, reload_at_time) == std::cv_status::timeout) {
            LOG(debug, "TLS config reload time reached, reloading file '%s'", _config_file_path.c_str());
            try_replace_current_engine();
            reload_at_time = make_future_reload_time_point();
        } // else: spurious wakeup or shutdown
    }
}

void AutoReloadingTlsCryptoEngine::try_replace_current_engine() {
    auto new_engine = try_create_engine_from_tls_config(_config_file_path, _authorization_mode);
    if (new_engine) {
        ConfigStatistics::get().inc_successful_config_reloads();
        std::lock_guard guard(_engine_mutex);
        _current_engine = std::move(new_engine);
    }
}

AutoReloadingTlsCryptoEngine::EngineSP AutoReloadingTlsCryptoEngine::acquire_current_engine() const {
    std::lock_guard guard(_engine_mutex);
    return _current_engine;
}

CryptoSocket::UP AutoReloadingTlsCryptoEngine::create_client_crypto_socket(SocketHandle socket, const SocketSpec &spec) {
    return acquire_current_engine()->create_client_crypto_socket(std::move(socket), spec);
}

CryptoSocket::UP AutoReloadingTlsCryptoEngine::create_server_crypto_socket(SocketHandle socket) {
    return acquire_current_engine()->create_server_crypto_socket(std::move(socket));
}

bool
AutoReloadingTlsCryptoEngine::use_tls_when_client() const
{
    return acquire_current_engine()->use_tls_when_client();
}

bool
AutoReloadingTlsCryptoEngine::always_use_tls_when_server() const
{
    return acquire_current_engine()->always_use_tls_when_server();
}

std::unique_ptr<CryptoCodec>
AutoReloadingTlsCryptoEngine::create_tls_client_crypto_codec(const SocketHandle &socket, const SocketSpec &spec) {
    return acquire_current_engine()->create_tls_client_crypto_codec(socket, spec);
}

std::unique_ptr<CryptoCodec>
AutoReloadingTlsCryptoEngine::create_tls_server_crypto_codec(const SocketHandle &socket) {
    return acquire_current_engine()->create_tls_server_crypto_codec(socket);
}

}
