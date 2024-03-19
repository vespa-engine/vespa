// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "countmetric.h"
#include "metricset.h"
#include "metricsnapshot.h"
#include "prometheus_writer.h"
#include "valuemetric.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <vespa/vespalib/util/small_vector.h>
#include <algorithm>
#include <cassert>
#include <cmath>

VESPALIB_HASH_SET_INSTANTIATE(vespalib::stringref);

using vespalib::ArrayRef;
using vespalib::ConstArrayRef;
using vespalib::stringref;
using vespalib::asciistream;

namespace metrics {

namespace {

[[nodiscard]] bool any_metric_in_path_has_nonempty_tag(const Metric& m) noexcept {
    const Metric* current = &m;
    do {
        if (std::ranges::any_of(current->getTags(), [](auto& t) noexcept { return t.hasValue(); })) {
            return true;
        }
        current = current->getOwner();
    } while (current != nullptr);
    return false;
}

[[nodiscard]] constexpr bool valid_prometheus_char(char ch) noexcept {
    // Prometheus also allows ':', but we don't.
    return ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_');
}

[[nodiscard]] bool valid_prometheus_name(stringref name) noexcept {
    return std::ranges::all_of(name, [](char ch) noexcept { return valid_prometheus_char(ch); });
}

[[nodiscard]] constexpr bool label_char_needs_escaping(char ch) noexcept {
    return (ch == '\\' || ch == '\n' || ch == '"');
}

[[nodiscard]] bool label_value_needs_escaping(stringref value) noexcept {
    return std::ranges::any_of(value, [](char ch) noexcept { return label_char_needs_escaping(ch); });
}

[[nodiscard]] vespalib::string prometheus_escaped_name(stringref str) {
    asciistream os;
    for (char ch : str) {
        if (valid_prometheus_char(ch)) [[likely]] {
            os << ch;
        } else {
            os << '_';
        }
    }
    return os.str();
}

[[nodiscard]] bool arrays_eq(ConstArrayRef<stringref> lhs, ConstArrayRef<stringref> rhs) noexcept {
    return std::ranges::equal(lhs, rhs);
}

[[nodiscard]] bool arrays_lt(ConstArrayRef<stringref> lhs, ConstArrayRef<stringref> rhs) noexcept {
    return std::ranges::lexicographical_compare(lhs, rhs);
}

}

PrometheusWriter::PrometheusWriter(asciistream& out)
    : MetricVisitor(),
      _arena(),
      _timestamp_str(),
      _samples(),
      _unique_str_refs(),
      _path(),
      _out(out)
{}

PrometheusWriter::~PrometheusWriter() = default;

bool PrometheusWriter::TimeSeriesSample::operator<(const TimeSeriesSample& rhs) const noexcept {
    // Standard multidimensional strict-weak ordering, with an indirection via
    // ConstArrayRefs for the first and last dimension.
    if (!arrays_eq(metric_path, rhs.metric_path)) {
        return arrays_lt(metric_path, rhs.metric_path);
    }
    if (aggr != rhs.aggr) {
        return aggr < rhs.aggr;
    }
    return arrays_lt(labels, rhs.labels);
}

stringref PrometheusWriter::arena_stable_string_ref(stringref str) {
    auto maybe_iter = _unique_str_refs.find(str);
    if (maybe_iter != _unique_str_refs.end()) {
        return *maybe_iter;
    }
    auto buf = _arena.create_uninitialized_array<char>(str.size());
    memcpy(buf.data(), str.data(), buf.size());
    stringref ref(buf.data(), buf.size());
    _unique_str_refs.insert(ref);
    return ref;
}

stringref PrometheusWriter::stable_name_string_ref(stringref raw_name) {
    if (valid_prometheus_name(raw_name)) [[likely]] {
        return arena_stable_string_ref(raw_name);
    } else {
        return arena_stable_string_ref(prometheus_escaped_name(raw_name));
    }
}

ConstArrayRef<stringref> PrometheusWriter::metric_to_path_ref(stringref leaf_metric_name) {
    vespalib::SmallVector<stringref, 16> path_refs;
    // _path strings are already in canonical (sanitized) form and arena-allocated
    for (const auto& p :_path) {
        path_refs.emplace_back(p);
    }
    path_refs.emplace_back(stable_name_string_ref(leaf_metric_name));
    return _arena.copy_array<stringref>({path_refs.data(), path_refs.size()});
}

vespalib::string PrometheusWriter::escaped_label_value(stringref value) {
    asciistream out;
    for (char ch : value) {
        if (ch == '\\') {
            out << "\\\\";
        } else if (ch == '"') {
            out << "\\\"";
        } else if (ch == '\n') {
            out << "\\n";
        } else [[likely]] {
            out << ch; // assumed to be part of a valid UTF-8 sequence
        }
    }
    return out.str();
}

stringref PrometheusWriter::stable_label_value_string_ref(stringref raw_label_value) {
    if (!label_value_needs_escaping(raw_label_value)) [[likely]] {
        return arena_stable_string_ref(raw_label_value);
    } else {
        return arena_stable_string_ref(escaped_label_value(raw_label_value));
    }
}

void PrometheusWriter::build_labels_upto_root(vespalib::SmallVector<stringref, 16>& out, const Metric& m) {
    const Metric* current = &m;
    do {
        for (const auto& tag : current->getTags()) {
            if (!tag.hasValue()) {
                continue; // Don't emit value-less tags, as these are not proper labels
            }
            out.emplace_back(stable_name_string_ref(tag.key()));
            out.emplace_back(stable_label_value_string_ref(tag.value()));
        }
        current = current->getOwner();
    } while (current != nullptr);
}

ConstArrayRef<stringref> PrometheusWriter::as_prometheus_labels(const Metric& m) {
    if (!any_metric_in_path_has_nonempty_tag(m)) {
        return {};
    }
    vespalib::SmallVector<stringref, 16> kv_refs;
    build_labels_upto_root(kv_refs, m);
    return _arena.copy_array<stringref>(kv_refs);
}

bool PrometheusWriter::visitSnapshot(const MetricSnapshot& ms) {
    // Pre-cache timestamp in string form to avoid same conversion for every time series
    _timestamp_str = std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(
            ms.getToTime().time_since_epoch()).count());
    return true;
}

void PrometheusWriter::doneVisitingSnapshot(const MetricSnapshot&) {
    // No-op
}

bool PrometheusWriter::visitMetricSet(const MetricSet& set, bool) {
    // Don't include metric sets that will be aggregated up into a separate sum metric.
    // We don't care about individual threads etc., just their aggregate values.
    if (set.hasTag("partofsum")) {
        return false;
    }
    if (set.getOwner()) {
        _path.emplace_back(stable_name_string_ref(set.getName()));
    } // else: don't add the topmost set
    return true;
}

void PrometheusWriter::doneVisitingMetricSet(const MetricSet& set) {
    if (set.getOwner()) {
        assert(!_path.empty());
        _path.pop_back();
    }
}

bool PrometheusWriter::visitCountMetric(const AbstractCountMetric& m, bool) {
    auto full_path = metric_to_path_ref(m.getName());
    auto labels    = as_prometheus_labels(m);
    _samples.emplace_back(full_path, "count", labels, m.getLongValue("count"));
    return true;
}

bool PrometheusWriter::visitValueMetric(const AbstractValueMetric& m, bool) {
    auto full_path = metric_to_path_ref(m.getName());
    auto labels    = as_prometheus_labels(m);
    _samples.emplace_back(full_path, "count", labels, m.getLongValue("count"));
    _samples.emplace_back(full_path, "sum",   labels, m.getDoubleValue("total"));
    _samples.emplace_back(full_path, "min",   labels, m.getDoubleValue("min"));
    _samples.emplace_back(full_path, "max",   labels, m.getDoubleValue("max"));
    return true;
}

void PrometheusWriter::render_path_as_metric_name_prefix(asciistream& out, ConstArrayRef<stringref> path) {
    for (const auto& p : path) {
        out << p << '_';
    }
}

void PrometheusWriter::render_label_pairs(asciistream& out, ConstArrayRef<stringref> labels) {
    if (!labels.empty()) {
        assert((labels.size() % 2) == 0);
        out << '{';
        for (size_t i = 0; i < labels.size(); i += 2) {
            if (i > 0) {
                out << ',';
            }
            // We expect both label key and value to be pre-normalized/sanitized.
            out << labels[i] << "=\"" << labels[i + 1] << '"';
        }
        out << '}';
    }
}

void PrometheusWriter::render_sample_value(asciistream& out, I64OrDouble value) {
    if (std::holds_alternative<double>(value)) {
        const double v = std::get<double>(value);
        const bool inf = std::isinf(v);
        const bool nan = std::isnan(v);
        // Prometheus allows "-Inf", "+Inf" and "NaN" as special values for negative infinity,
        // positive infinity and "not a number", respectively.
        if (!inf && !nan) [[likely]] {
            out << asciistream::Precision(16) << vespalib::automatic << v;
        } else if (inf) {
            out << (v < 0.0 ? "-Inf" : "+Inf");
        } else {
            out << "NaN";
        }
    } else {
        const int64_t v = std::get<int64_t>(value);
        out << v;
    }
}

void PrometheusWriter::doneVisiting() {
    _out << "# NOTE: THIS API IS NOT INTENDED FOR PUBLIC USE\n";
    // Sort and implicitly group all related metrics together, ordered by name -> aggregation -> dimensions
    std::sort(_samples.begin(), _samples.end());
    ConstArrayRef<stringref> last_metric;
    stringref last_aggr;
    for (const auto& s : _samples) {
        if ((s.aggr != last_aggr) || !arrays_eq(s.metric_path, last_metric)) {
            _out << "# TYPE ";
            render_path_as_metric_name_prefix(_out, s.metric_path);
            _out << s.aggr << " untyped\n";
            last_metric = s.metric_path;
            last_aggr   = s.aggr;
        }
        render_path_as_metric_name_prefix(_out, s.metric_path);
        _out << s.aggr;
        render_label_pairs(_out, s.labels);
        _out << ' ';
        render_sample_value(_out, s.value);
        _out << ' ' << _timestamp_str << '\n';
    }
}

}
