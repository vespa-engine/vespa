// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "point_map_collection.h"
#include <assert.h>

namespace vespalib {
namespace metrics {

HashedPointMap::HashedPointMap(PointMap &&from)
    : _map(std::move(from)),
      _hash(0)
{
    for (const PointMap::value_type &entry : _map) {
        _hash = (_hash << 7) + (_hash >> 31) + entry.first.id();
        _hash = (_hash << 7) + (_hash >> 31) + entry.second.id();
    }
}

bool
HashedPointMap::operator< (const HashedPointMap &other) const
{
    // cheap comparison first
    if (_hash != other._hash) {
        return _hash < other._hash;
    }
    if (_map.size() != other._map.size()) {
        return _map.size() < other._map.size();
    }
    // sizes equal, fall back to std::map::operator<
    return _map < other._map; 
}

using Guard = std::lock_guard<std::mutex>;

const PointMap &
PointMapCollection::lookup(size_t id) const
{
    Guard guard(_lock);
    assert(id < _vec.size());
    PointMapMap::const_iterator iter = _vec[id];
    return iter->first.backingMap();
}

size_t
PointMapCollection::resolve(PointMap map)
{
    Guard guard(_lock);
    size_t nextId = _vec.size();
    auto iter_check = _map.emplace(std::move(map), nextId);
    if (iter_check.second) {
        _vec.push_back(iter_check.first);
    }
    return iter_check.first->second;
}

size_t
PointMapCollection::size() const
{
    Guard guard(_lock);
    return _vec.size();
}

} // namespace vespalib::metrics
} // namespace vespalib
