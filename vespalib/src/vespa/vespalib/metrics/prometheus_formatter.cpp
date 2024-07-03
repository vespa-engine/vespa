// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "prometheus_formatter.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <algorithm>
#include <cmath>

namespace vespalib::metrics {

namespace {

[[nodiscard]] constexpr bool valid_prometheus_char(char ch) noexcept {
    // Prometheus also allows ':', but we don't.
    return ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_');
}

void emit_prometheus_name(asciistream& out, std::string_view name) {
    for (char ch : name) {
        if (valid_prometheus_char(ch)) [[likely]] {
            out << ch;
        } else {
            out << '_';
        }
    }
}

void emit_label_value(asciistream& out, std::string_view value) {
    for (char ch : value) {
        if (ch == '\\') {
            out << "\\\\";
        } else if (ch == '\n') {
            out << "\\n";
        } else if (ch == '\"') {
            out << "\\\"";
        } else [[likely]] {
            out << ch;
        }
    }
}

void emit_point_as_labels(asciistream& out, const PointSnapshot& point) {
    if (point.dimensions.empty()) {
        return; // No '{}' suffix if no dimensions are present.
    }
    out << '{';
    for (size_t i = 0; i < point.dimensions.size(); ++i) {
        if (i > 0) {
            out << ',';
        }
        auto& dim = point.dimensions[i];
        emit_prometheus_name(out, dim.dimensionName());
        out << "=\"";
        emit_label_value(out, dim.labelValue());
        out << '"';
    }
    out << '}';
}

void emit_sanitized_double(asciistream& out, double v) {
    const bool inf = std::isinf(v);
    const bool nan = std::isnan(v);
    if (!inf && !nan) [[likely]] {
        out << asciistream::Precision(16) << automatic << v;
    } else if (inf) {
        out << (v < 0.0 ? "-Inf" : "+Inf");
    } else {
        out << "NaN";
    }
}

} // anon ns

PrometheusFormatter::PrometheusFormatter(const vespalib::metrics::Snapshot& snapshot)
    : _snapshot(snapshot),
      // TODO timestamp should be a chrono unit, not seconds as a double (here converted to millis explicitly)
      _timestamp_str(std::to_string(static_cast<uint64_t>(_snapshot.endTime() * 1000.0)))
{
}

PrometheusFormatter::~PrometheusFormatter() = default;

void PrometheusFormatter::emit_counter(asciistream& out, const CounterSnapshot& cs) const {
    emit_prometheus_name(out, cs.name());
    emit_point_as_labels(out, cs.point());
    out << ' ' << cs.count() << ' ' << _timestamp_str << '\n';
}

const char* PrometheusFormatter::sub_metric_type_str(SubMetric m) noexcept {
    switch (m) {
    case SubMetric::Count: return "count";
    case SubMetric::Sum:   return "sum";
    case SubMetric::Min:   return "min";
    case SubMetric::Max:   return "max";
    }
    abort();
}

void PrometheusFormatter::emit_gauge(asciistream& out, const GaugeSnapshot& gs, SubMetric m) const {
    emit_prometheus_name(out, gs.name());
    out << '_' << sub_metric_type_str(m);
    emit_point_as_labels(out, gs.point());
    out << ' ';
    switch (m) {
    case SubMetric::Count: out << gs.observedCount(); break;
    case SubMetric::Sum:   emit_sanitized_double(out, gs.sumValue()); break;
    case SubMetric::Min:   emit_sanitized_double(out, gs.minValue()); break;
    case SubMetric::Max:   emit_sanitized_double(out, gs.maxValue()); break;
    }
    out << ' ' << _timestamp_str << '\n';
}

void PrometheusFormatter::emit_counters(vespalib::asciistream& out) const {
    std::vector<const CounterSnapshot*> ordered_counters;
    ordered_counters.reserve(_snapshot.counters().size());
    for (const auto& cs : _snapshot.counters()) {
        ordered_counters.emplace_back(&cs); // We expect instances to be stable during processing.
    }
    std::ranges::sort(ordered_counters, [](auto& lhs, auto& rhs) noexcept {
        return lhs->name() < rhs->name();
    });
    for (const auto* cs : ordered_counters) {
        emit_counter(out, *cs);
    }
}

void PrometheusFormatter::emit_gauges(vespalib::asciistream& out) const {
    std::vector<std::pair<const GaugeSnapshot*, SubMetric>> ordered_gauges;
    ordered_gauges.reserve(_snapshot.gauges().size() * NumSubMetrics);
    for (const auto& gs : _snapshot.gauges()) {
        ordered_gauges.emplace_back(&gs, SubMetric::Count);
        ordered_gauges.emplace_back(&gs, SubMetric::Sum);
        ordered_gauges.emplace_back(&gs, SubMetric::Min);
        ordered_gauges.emplace_back(&gs, SubMetric::Max);
    }
    // Group all related time series together, ordered by name -> sub metric.
    std::ranges::sort(ordered_gauges, [](auto& lhs, auto& rhs) noexcept {
        if (lhs.first->name() != rhs.first->name()) {
            return lhs.first->name() < rhs.first->name();
        }
        return lhs.second < rhs.second;
    });
    for (const auto& gs : ordered_gauges) {
        emit_gauge(out, *gs.first, gs.second);
    }
}

vespalib::string PrometheusFormatter::as_text_formatted() const {
    asciistream out;
    emit_counters(out);
    emit_gauges(out);
    return out.str();
}

} // vespalib::metrics
