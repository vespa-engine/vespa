// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <atomic>
#include <stdint.h>

namespace vespalib::net::tls {

/**
 * Low-level statistics set by connection and credential management code
 * for TLS and insecure plaintext connections.
 *
 * A poor man's substitute for not currently having the ability to natively
 * export metrics in vespalib. Should be removed in favor of proper metrics
 * once this is possible.
 *
 * Fully thread safe.
 */
struct ConnectionStatistics {

    // Number of insecure (legacy) plaintext connections established
    std::atomic<uint64_t> insecure_connections     = 0;
    // Number of TLS connections successfully established. Note that
    // the handshake has to succeed for a connection to be counted here.
    std::atomic<uint64_t> tls_connections          = 0;
    // Number of connections that failed during the TLS handshake process.
    // May be caused by bad certificates, invalid credentials, bad ciphers etc.
    std::atomic<uint64_t> failed_tls_handshakes    = 0;
    // Number of connections rejected because the certificate did not have
    // credentials that matched the requirements given in the TLS config file.
    std::atomic<uint64_t> invalid_peer_credentials = 0;
    // Number of connections broken due to errors during TLS encoding or decoding
    std::atomic<uint64_t> broken_tls_connections   = 0;

    void inc_insecure_connections() noexcept {
        insecure_connections.fetch_add(1, std::memory_order_relaxed);
    }
    void inc_tls_connections() noexcept {
        tls_connections.fetch_add(1, std::memory_order_relaxed);
    }
    void inc_failed_tls_handshakes() noexcept {
        failed_tls_handshakes.fetch_add(1, std::memory_order_relaxed);
    }
    void inc_invalid_peer_credentials() noexcept {
        invalid_peer_credentials.fetch_add(1, std::memory_order_relaxed);
    }
    void inc_broken_tls_connections() noexcept {
        broken_tls_connections.fetch_add(1, std::memory_order_relaxed);
    }

    struct Snapshot {
        uint64_t insecure_connections     = 0;
        uint64_t tls_connections          = 0;
        uint64_t failed_tls_handshakes    = 0;
        uint64_t invalid_peer_credentials = 0;
        uint64_t broken_tls_connections   = 0;

        [[nodiscard]] Snapshot subtract(const Snapshot& rhs) const noexcept;
    };

    // Acquires a snapshot of statistics that is expected to be reasonably up-to-date.
    // Thread safe.
    [[nodiscard]] Snapshot snapshot() const noexcept;

    static ConnectionStatistics client_stats;
    static ConnectionStatistics server_stats;

    static ConnectionStatistics& get(bool is_server) noexcept {
        return (is_server ? server_stats : client_stats);
    }
};

struct ConfigStatistics {
    std::atomic<uint64_t> successful_config_reloads = 0;
    std::atomic<uint64_t> failed_config_reloads     = 0;

    void inc_successful_config_reloads() noexcept {
        successful_config_reloads.fetch_add(1, std::memory_order_relaxed);
    }
    void inc_failed_config_reloads() noexcept {
        failed_config_reloads.fetch_add(1, std::memory_order_relaxed);
    }

    struct Snapshot {
        uint64_t successful_config_reloads = 0;
        uint64_t failed_config_reloads     = 0;

        [[nodiscard]] Snapshot subtract(const Snapshot& rhs) const noexcept;
    };

    // Acquires a snapshot of statistics that is expected to be reasonably up-to-date.
    // Thread safe.
    [[nodiscard]] Snapshot snapshot() const noexcept;

    static ConfigStatistics instance;
    static ConfigStatistics& get() noexcept { return instance; }
};

struct CapabilityStatistics {
    std::atomic<uint64_t> rpc_capability_checks_failed    = 0;
    std::atomic<uint64_t> status_capability_checks_failed = 0;

    void inc_rpc_capability_checks_failed() noexcept {
        rpc_capability_checks_failed.fetch_add(1, std::memory_order_relaxed);
    }

    void inc_status_capability_checks_failed() noexcept {
        status_capability_checks_failed.fetch_add(1, std::memory_order_relaxed);
    }

    struct Snapshot {
        uint64_t rpc_capability_checks_failed    = 0;
        uint64_t status_capability_checks_failed = 0;

        [[nodiscard]] Snapshot subtract(const Snapshot& rhs) const noexcept;
    };

    // Acquires a snapshot of statistics that is expected to be reasonably up-to-date.
    // Thread safe.
    [[nodiscard]] Snapshot snapshot() const noexcept;

    static CapabilityStatistics instance;
    static CapabilityStatistics& get() noexcept { return instance; }
};

}
