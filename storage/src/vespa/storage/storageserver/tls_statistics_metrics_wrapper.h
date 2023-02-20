// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/metrics/metricset.h>
#include <vespa/metrics/countmetric.h>
#include <vespa/vespalib/net/tls/statistics.h>

#include <chrono>

namespace storage {

// Simple wrapper around low-level vespalib network statistics which
// converts the monotonically increasing counters to deltas during
// periodic metric snapshotting.
class TlsStatisticsMetricsWrapper : public metrics::MetricSet {
    struct EndpointMetrics : metrics::MetricSet {
        EndpointMetrics(vespalib::stringref type, metrics::MetricSet* owner);
        ~EndpointMetrics() override;

        metrics::LongCountMetric tls_connections_established;
        metrics::LongCountMetric insecure_connections_established;
    };

    EndpointMetrics client;
    EndpointMetrics server;
    metrics::LongCountMetric tls_handshakes_failed;
    metrics::LongCountMetric peer_authorization_failures;
    metrics::LongCountMetric tls_connections_broken;

    metrics::LongCountMetric failed_tls_config_reloads;

    metrics::LongCountMetric rpc_capability_checks_failed;
    metrics::LongCountMetric status_capability_checks_failed;

    vespalib::net::tls::ConnectionStatistics::Snapshot last_client_stats_snapshot;
    vespalib::net::tls::ConnectionStatistics::Snapshot last_server_stats_snapshot;
    vespalib::net::tls::ConfigStatistics::Snapshot     last_config_stats_snapshot;
    vespalib::net::tls::CapabilityStatistics::Snapshot last_capability_stats_snapshot;

public:
    explicit TlsStatisticsMetricsWrapper(metrics::MetricSet* owner);
    ~TlsStatisticsMetricsWrapper() override;

    void update_metrics_with_snapshot_delta();
};

}
