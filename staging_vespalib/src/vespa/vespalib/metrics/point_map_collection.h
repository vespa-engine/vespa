// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <mutex>
#include <map>
#include <vector>
#include "point_map.h"

namespace vespalib {
namespace metrics {

// internal
class HashedPointMap {
private:
    const PointMap _map;
    size_t _hash;
public:
    HashedPointMap(PointMap &&from);
    bool operator< (const HashedPointMap &other) const;

    const PointMap &backingMap() const { return _map; }
};

// internal
class PointMapCollection {
private:
    using PointMapMap = std::map<HashedPointMap, size_t>;

    mutable std::mutex _lock;
    PointMapMap _map;
    std::vector<PointMapMap::const_iterator> _vec;
public:
    const PointMap &lookup(size_t id) const;
    size_t resolve(PointMap map);
    size_t size() const;

    PointMapCollection() = default;
    ~PointMapCollection() {}
};

} // namespace vespalib::metrics
} // namespace vespalib
