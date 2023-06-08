// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitvector_search_cache.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <mutex>

namespace search::attribute {

BitVectorSearchCache::BitVectorSearchCache()
    : _mutex(),
      _size(0),
      _cache()
{}

BitVectorSearchCache::~BitVectorSearchCache() = default;

void
BitVectorSearchCache::insert(const vespalib::string &term, std::shared_ptr<Entry> entry)
{
    std::unique_lock guard(_mutex);
    _cache.insert(std::make_pair(term, std::move(entry)));
    _size.store(_cache.size());
}

std::shared_ptr<BitVectorSearchCache::Entry>
BitVectorSearchCache::find(const vespalib::string &term) const
{
    if (size() > 0ul) {
        std::shared_lock guard(_mutex);
        auto itr = _cache.find(term);
        if (itr != _cache.end()) {
            return itr->second;
        }
    }
    return {};
}

void
BitVectorSearchCache::clear()
{
    std::unique_lock guard(_mutex);
    _cache.clear();
    _size.store(0ul, std::memory_order_relaxed);
}

}
