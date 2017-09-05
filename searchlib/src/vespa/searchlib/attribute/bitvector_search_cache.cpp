// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitvector_search_cache.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace search::attribute {

using BitVectorSP = BitVectorSearchCache::BitVectorSP;

BitVectorSearchCache::BitVectorSearchCache()
    : _mutex(),
      _cache()
{
}

BitVectorSearchCache::~BitVectorSearchCache()
{
}

void
BitVectorSearchCache::insert(const vespalib::string &term, BitVectorSP bitVector)
{
    LockGuard guard(_mutex);
    _cache.insert(std::make_pair(term, std::move(bitVector)));
}

BitVectorSP
BitVectorSearchCache::find(const vespalib::string &term) const
{
    LockGuard guard(_mutex);
    auto itr = _cache.find(term);
    if (itr != _cache.end()) {
        return itr->second;
    }
    return BitVectorSP();
}

size_t
BitVectorSearchCache::size() const
{
    LockGuard guard(_mutex);
    return _cache.size();
}

void
BitVectorSearchCache::clear()
{
    LockGuard guard(_mutex);
    _cache.clear();
}

}
