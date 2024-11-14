// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_posting_list_cache.h"

namespace search::diskindex {

/*
 * Class for caching posting lists read from disk.
 * It uses an LRU cache from vespalib.
 */
class PostingListCache : public IPostingListCache {
public:
    class BackingStore;
private:
    class Cache;
    class BitVectorCache;
    std::unique_ptr<const BackingStore> _backing_store;
    std::unique_ptr<Cache> _cache;
    std::unique_ptr<BitVectorCache> _bitvector_cache;
public:
    PostingListCache(size_t max_bytes, size_t bitvector_max_bytes);
    ~PostingListCache() override;
    search::index::PostingListHandle read(const Key& key, Context& ctx) const override;
    std::shared_ptr<BitVector> read(const BitVectorKey& key, Context& ctx) const override;
    vespalib::CacheStats get_stats() const override;
    vespalib::CacheStats get_bitvector_stats() const override;
    bool enabled_for_posting_lists() const noexcept override;
    bool enabled_for_bitvectors() const noexcept override;
    static size_t element_size();
    static size_t bitvector_element_size();
};

}
