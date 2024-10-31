// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_posting_list_cache.h"

namespace search::diskindex {

/*
 * Class for caching posting lists.
 */
class PostingListCache : public IPostingListCache {
public:
    class BackingStore;
private:
    class Cache;
    std::unique_ptr<const BackingStore> _backing_store;
    std::unique_ptr<Cache> _cache;
public:
    PostingListCache(size_t max_bytes);
    ~PostingListCache() override;
    search::index::PostingListHandle read(const Key& key) const override;
    vespalib::CacheStats get_stats() const override;
    static size_t element_size();
};

}
