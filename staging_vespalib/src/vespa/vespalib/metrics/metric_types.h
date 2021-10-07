// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <mutex>
#include <vector>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace metrics {

// internal class for typechecking
class MetricTypes {
    static const char *_typeNames[];
public:
    enum MetricType {
        INVALID,
        COUNTER,
        GAUGE,
        HISTOGRAM,
        INT_HISTOGRAM
    };

    void check(size_t id, const vespalib::string& name, MetricType ty);

    MetricTypes() = default;
    ~MetricTypes() {}
private:
    std::mutex _lock;
    std::vector<MetricType> _seen;
};

} // namespace vespalib::metrics
} // namespace vespalib
