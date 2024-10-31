// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "posting_list_cache.h"
#include <vespa/searchlib/index/dictionary_lookup_result.h>
#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/vespalib/stllike/cache.hpp>
#include <iostream>

using search::index::DictionaryLookupResult;
using search::index::PostingListHandle;

namespace search::diskindex {

class PostingListCache::BackingStore
{
public:
   BackingStore();
   ~BackingStore();
   bool read(const IPostingListCache::Key& key, PostingListHandle& value) const;
};

PostingListCache::BackingStore::BackingStore() = default;
PostingListCache::BackingStore::~BackingStore() = default;

bool
PostingListCache::BackingStore::read(const IPostingListCache::Key& key, PostingListHandle& value) const
{
    // TODO: Store a smaller copy if posting list is small
    value = key.backing_store_file->read(key);
    return true;
}

struct PostingListHandleSize {
    size_t operator() (const PostingListHandle & arg) const noexcept { return arg._allocSize; }
};

using CacheParams = vespalib::CacheParam<
    vespalib::LruParam<IPostingListCache::Key, PostingListHandle>,
    const PostingListCache::BackingStore,
    vespalib::zero<IPostingListCache::Key>,
    PostingListHandleSize
>;

class PostingListCache::Cache : public vespalib::cache<CacheParams> {
public:
    using Parent = vespalib::cache<CacheParams>;
    Cache(BackingStore& backing_store, size_t max_bytes);
    ~Cache();
    static size_t element_size() { return sizeof(value_type); }
};

PostingListCache::Cache::Cache(BackingStore& backing_store, size_t max_bytes)
    : Parent(backing_store, max_bytes)
{
}

PostingListCache::Cache::~Cache() = default;

PostingListCache::PostingListCache(size_t max_bytes)
    : IPostingListCache(),
      _backing_store(std::make_unique<BackingStore>()),
      _cache(std::make_unique<Cache>(*_backing_store, max_bytes))
{
}

PostingListCache::~PostingListCache() = default;

PostingListHandle
PostingListCache::read(const Key& key) const
{
    return _cache->read(key);
}

vespalib::CacheStats
PostingListCache::get_stats() const
{
    return _cache->get_stats();
}

size_t
PostingListCache::element_size()
{
    return PostingListCache::Cache::element_size();
}

}
