// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitvector_search_cache.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/memoryusage.h>
#include <mutex>

namespace search::attribute {

BitVectorSearchCache::BitVectorSearchCache()
    : _mutex(),
      _size(0),
      _entries_extra_memory_usage(0),
      _cache()
{}

BitVectorSearchCache::~BitVectorSearchCache() = default;

void
BitVectorSearchCache::insert(const vespalib::string &term, std::shared_ptr<Entry> entry)
{
    size_t entry_extra_memory_usage = 0;
    if (entry) {
        entry_extra_memory_usage = sizeof(Entry);
        if (entry->bitVector) {
            entry_extra_memory_usage += entry->bitVector->getFileBytes();
        }
    }
    std::unique_lock guard(_mutex);
    auto ins_res = _cache.insert(std::make_pair(term, std::move(entry)));
    _size.store(_cache.size());
    if (ins_res.second) {
        _entries_extra_memory_usage += entry_extra_memory_usage;
    }
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

vespalib::MemoryUsage
BitVectorSearchCache::get_memory_usage() const
{
    std::lock_guard guard(_mutex);
    size_t cache_memory_consumption = _cache.getMemoryConsumption();
    size_t cache_memory_used = _cache.getMemoryUsed();
    size_t self_memory_used = sizeof(BitVectorSearchCache) - sizeof(_cache);
    size_t allocated = self_memory_used + cache_memory_consumption + _entries_extra_memory_usage;
    size_t used = self_memory_used + cache_memory_used + _entries_extra_memory_usage;
    return vespalib::MemoryUsage(allocated, used, 0, 0);
}

void
BitVectorSearchCache::clear()
{
    std::unique_lock guard(_mutex);
    _cache.clear();
    _size.store(0ul, std::memory_order_relaxed);
    _entries_extra_memory_usage = 0;
}

}
