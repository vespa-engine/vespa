// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tls_statistics_metrics_wrapper.h"

namespace storage {

TlsStatisticsMetricsWrapper::EndpointMetrics::EndpointMetrics(vespalib::stringref type, metrics::MetricSet* owner)
    : metrics::MetricSet(type, {}, "Endpoint type metrics", owner),
      tls_connections_established("tls-connections-established", {},
              "Number of secure mTLS connections established", this),
      insecure_connections_established("insecure-connections-established", {},
              "Number of insecure (plaintext) connections established", this)
{}

TlsStatisticsMetricsWrapper::EndpointMetrics::~EndpointMetrics() = default;

TlsStatisticsMetricsWrapper::TlsStatisticsMetricsWrapper(metrics::MetricSet* owner)
    : metrics::MetricSet("network", {}, "Network connection metrics", owner),
      client("client", this),
      server("server", this),
      tls_handshakes_failed("tls-handshakes-failed", {}, "Number of client or "
              "server connection attempts that failed during TLS handshaking", this),
      peer_authorization_failures("peer-authorization-failures", {},
              "Number of TLS connection attempts failed due to bad or missing "
              "peer certificate credentials", this),
      tls_connections_broken("tls-connections-broken", {}, "Number of TLS "
              "connections broken due to failures during frame encoding or decoding", this),
      failed_tls_config_reloads("failed-tls-config-reloads", {}, "Number of times "
              "background reloading of TLS config has failed", this),
      rpc_capability_checks_failed("rpc-capability-checks-failed", {},
              "Number of RPC operations that failed to due one or more missing capabilities", this),
      status_capability_checks_failed("status-capability-checks-failed", {},
              "Number of status page operations that failed to due one or more missing capabilities", this),
      last_client_stats_snapshot(),
      last_server_stats_snapshot(),
      last_config_stats_snapshot(),
      last_capability_stats_snapshot()
{}

TlsStatisticsMetricsWrapper::~TlsStatisticsMetricsWrapper() = default;

void TlsStatisticsMetricsWrapper::update_metrics_with_snapshot_delta() {
    auto server_current = vespalib::net::tls::ConnectionStatistics::get(true).snapshot();
    auto client_current = vespalib::net::tls::ConnectionStatistics::get(false).snapshot();
    auto server_delta = server_current.subtract(last_server_stats_snapshot);
    auto client_delta = client_current.subtract(last_client_stats_snapshot);

    client.insecure_connections_established.set(client_delta.insecure_connections);
    client.tls_connections_established.set(client_delta.tls_connections);
    server.insecure_connections_established.set(server_delta.insecure_connections);
    server.tls_connections_established.set(server_delta.tls_connections);

    // We have underlying stats for both server and client here, but for the
    // moment we just aggregate them up into combined metrics. Can be trivially
    // split up into separate metrics later if deemed useful.
    tls_handshakes_failed.set(client_delta.failed_tls_handshakes +
                              server_delta.failed_tls_handshakes);
    peer_authorization_failures.set(client_delta.invalid_peer_credentials +
                                    server_delta.invalid_peer_credentials);
    tls_connections_broken.set(client_delta.broken_tls_connections +
                               server_delta.broken_tls_connections);

    auto config_current = vespalib::net::tls::ConfigStatistics::get().snapshot();
    auto config_delta = config_current.subtract(last_config_stats_snapshot);

    failed_tls_config_reloads.set(config_delta.failed_config_reloads);

    auto capability_current = vespalib::net::tls::CapabilityStatistics::get().snapshot();
    auto capability_delta = capability_current.subtract(last_capability_stats_snapshot);

    rpc_capability_checks_failed.set(capability_delta.rpc_capability_checks_failed);
    status_capability_checks_failed.set(capability_delta.status_capability_checks_failed);

    last_server_stats_snapshot = server_current;
    last_client_stats_snapshot = client_current;
    last_config_stats_snapshot = config_current;
    last_capability_stats_snapshot = capability_current;
}

}
