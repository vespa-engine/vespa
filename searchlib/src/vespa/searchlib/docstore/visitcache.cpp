// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitcache.h"

namespace search {
namespace docstore {

bool
KeySet::contains(const KeySet &rhs) const {
    if (rhs._keys.size() > _keys.size()) { return false; }

    uint32_t b(0);
    for (uint32_t a(0); a < _keys.size() && b < rhs._keys.size();) {
        if (_keys[a] < rhs._keys[b]) {
            a++;
        } else if (_keys[a] == rhs._keys[b]) {
            a++;
            b++;
        } else {
            return false;
        }
    }
    return b == rhs._keys.size();
}

VisitCache::VisitCache(IDataStore &store, size_t cacheSize, const document::CompressionConfig &compression) :
    _store(store, compression),
    _cache(new Cache(_store, cacheSize))
{
}

CompressedBlobSet
VisitCache::read(const Keys & keys) const {
    return _cache.read(keys);
}

void
VisitCache::remove(uint32_t key) {
    // shall modify the cached element
    (void) key;
}

}
}

