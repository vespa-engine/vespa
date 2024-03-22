// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "snapshots.h"
#include <vespa/vespalib/stllike/string.h>
#include <string>

namespace vespalib { class asciistream; }

namespace vespalib::metrics {

/**
 * Utility for formatting a metric Snapshot as Prometheus v0.0.4 text output.
 *
 * Note: we do not emit any `TYPE` information in the output, which means that
 * all metrics are implicitly treated by the receiver as untyped. This is also
 * the most conservative option since non-cumulative snapshots do not have
 * monotonic counters, which violates Prometheus data model expectations.
 */
class PrometheusFormatter {
    const Snapshot& _snapshot;
    std::string     _timestamp_str;
public:
    explicit PrometheusFormatter(const Snapshot& snapshot);
    ~PrometheusFormatter();

    [[nodiscard]] vespalib::string as_text_formatted() const;
private:
    enum SubMetric { Count, Sum, Min, Max };
    constexpr static uint32_t NumSubMetrics = 4; // Must match the enum cardinality of SubMetric

    [[nodiscard]] static const char* sub_metric_type_str(SubMetric m) noexcept;

    void emit_counter(vespalib::asciistream& out, const CounterSnapshot& cs) const;
    void emit_counters(vespalib::asciistream& out) const;
    void emit_gauge(vespalib::asciistream& out, const GaugeSnapshot& cs, SubMetric m) const;
    void emit_gauges(vespalib::asciistream& out) const;
};

}
