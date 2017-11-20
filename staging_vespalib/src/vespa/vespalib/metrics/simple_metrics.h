// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <chrono>
#include <memory>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace metrics {

class MetricsCollector;

struct MetricIdentifier {
    const size_t name_idx;
    const size_t point_idx;

    MetricIdentifier() : name_idx(-1), point_idx(0) {}

    explicit MetricIdentifier(size_t id)
      : name_idx(id), point_idx(0) {}

    MetricIdentifier(size_t id, size_t pt)
      : name_idx(id), point_idx(pt) {}

    bool operator< (const MetricIdentifier &other) const {
        if (name_idx < other.name_idx) return true;
        if (name_idx == other.name_idx) {
            return (point_idx < other.point_idx);
        }
        return false;
    }
    bool operator== (const MetricIdentifier &other) const {
        return (name_idx == other.name_idx &&
                point_idx == other.point_idx);
    }
};

} // namespace vespalib::metrics
} // namespace vespalib

namespace std
{
    template<> struct hash<vespalib::metrics::MetricIdentifier>
    {
        typedef vespalib::metrics::MetricIdentifier argument_type;
        typedef std::size_t result_type;
        result_type operator()(argument_type const& ident) const noexcept
        {
            return (ident.point_idx << 20) + ident.name_idx;
        }
    };
}
