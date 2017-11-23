// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace metrics {

using AxisName = vespalib::string;

class Axis {
    const size_t _axis_idx;
public:
    size_t id() const { return _axis_idx; }
    Axis(size_t id) : _axis_idx(id) {}
    bool operator< (const Axis &other) const { return id() < other.id(); }
};

} // namespace vespalib::metrics
} // namespace vespalib
