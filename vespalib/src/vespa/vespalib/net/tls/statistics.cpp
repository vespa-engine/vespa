// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statistics.h"

namespace vespalib::net::tls {

ConnectionStatistics ConnectionStatistics::client_stats = {};
ConnectionStatistics ConnectionStatistics::server_stats = {};

ConfigStatistics ConfigStatistics::instance = {};

CapabilityStatistics CapabilityStatistics::instance = {};

ConnectionStatistics::Snapshot ConnectionStatistics::snapshot() const noexcept {
    Snapshot s;
    s.insecure_connections       = insecure_connections.load(std::memory_order_relaxed);
    s.tls_connections            = tls_connections.load(std::memory_order_relaxed);
    s.failed_tls_handshakes      = failed_tls_handshakes.load(std::memory_order_relaxed);
    s.invalid_peer_credentials   = invalid_peer_credentials.load(std::memory_order_relaxed);
    s.broken_tls_connections     = broken_tls_connections.load(std::memory_order_relaxed);
    return s;
}

ConnectionStatistics::Snapshot ConnectionStatistics::Snapshot::subtract(const Snapshot& rhs) const noexcept {
    Snapshot s;
    s.insecure_connections     = insecure_connections     - rhs.insecure_connections;
    s.tls_connections          = tls_connections          - rhs.tls_connections;
    s.failed_tls_handshakes    = failed_tls_handshakes    - rhs.failed_tls_handshakes;
    s.invalid_peer_credentials = invalid_peer_credentials - rhs.invalid_peer_credentials;
    s.broken_tls_connections   = broken_tls_connections   - rhs.broken_tls_connections;
    return s;
}

ConfigStatistics::Snapshot ConfigStatistics::snapshot() const noexcept {
    Snapshot s;
    s.successful_config_reloads = successful_config_reloads.load(std::memory_order_relaxed);
    s.failed_config_reloads     = failed_config_reloads.load(std::memory_order_relaxed);
    return s;
}

ConfigStatistics::Snapshot ConfigStatistics::Snapshot::subtract(const Snapshot& rhs) const noexcept {
    Snapshot s;
    s.successful_config_reloads = successful_config_reloads - rhs.successful_config_reloads;
    s.failed_config_reloads     = failed_config_reloads     - rhs.failed_config_reloads;
    return s;
}

CapabilityStatistics::Snapshot CapabilityStatistics::snapshot() const noexcept {
    Snapshot s;
    s.rpc_capability_checks_failed    = rpc_capability_checks_failed.load(std::memory_order_relaxed);
    s.status_capability_checks_failed = status_capability_checks_failed.load(std::memory_order_relaxed);
    return s;
}

CapabilityStatistics::Snapshot CapabilityStatistics::Snapshot::subtract(const Snapshot& rhs) const noexcept {
    Snapshot s;
    s.rpc_capability_checks_failed    = rpc_capability_checks_failed - rhs.rpc_capability_checks_failed;
    s.status_capability_checks_failed = status_capability_checks_failed - rhs.status_capability_checks_failed;
    return s;
}

}
