// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include "dimension.h"
#include "label.h"

namespace vespalib {
namespace metrics {

using PointMap = std::map<Dimension, Label>;

// internal
class HashedPointMap {
private:
    const PointMap _map;
    size_t _hash;
public:
    HashedPointMap() : _map(), _hash(0) {}
    HashedPointMap(PointMap &&from);
    bool operator< (const HashedPointMap &other) const;

    const PointMap &backingMap() const { return _map; }
};

} // namespace vespalib::metrics
} // namespace vespalib
