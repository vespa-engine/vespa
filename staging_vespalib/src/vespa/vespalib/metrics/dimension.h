// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace metrics {

using DimensionName = vespalib::string;

class Dimension {
    const size_t _dimension_idx;
public:
    size_t id() const { return _dimension_idx; }
    Dimension(size_t id) : _dimension_idx(id) {}
    bool operator< (const Dimension &other) const { return id() < other.id(); }
};

} // namespace vespalib::metrics
} // namespace vespalib
