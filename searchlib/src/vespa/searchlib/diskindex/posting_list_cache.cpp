// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "posting_list_cache.h"
#include <vespa/searchlib/common/allocatedbitvector.h>
#include <vespa/searchlib/index/dictionary_lookup_result.h>
#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/vespalib/stllike/cache.hpp>
#include <iostream>
#include <vespa/searchlib/index/bitvector_dictionary_lookup_result.h>

using search::index::BitVectorDictionaryLookupResult;
using search::index::DictionaryLookupResult;
using search::index::PostingListHandle;

namespace search::diskindex {

class PostingListCache::BackingStore
{
public:
    BackingStore();
    ~BackingStore();
    bool read(const Key& key, PostingListHandle& value, Context& ctx) const;
    bool read(const BitVectorKey& key, std::shared_ptr<BitVector>& value, Context& ctx) const;
};

PostingListCache::BackingStore::BackingStore() = default;
PostingListCache::BackingStore::~BackingStore() = default;

bool
PostingListCache::BackingStore::read(const Key& key, PostingListHandle& value, Context& ctx) const
{
    // TODO: Store a smaller copy if posting list is small
    value = ctx.backing_store_file->read(key, ctx);
    return true;
}

bool
PostingListCache::BackingStore::read(const BitVectorKey& key, std::shared_ptr<BitVector>& value, Context& ctx) const
{
    value = ctx.backing_store_file->read(key, ctx);
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

struct BitVectorCacheValueSize {
    size_t operator() (const std::shared_ptr<BitVector>& bv) const noexcept { return bv->get_allocated_bytes(true); }
};

using BitVectorCacheParams = vespalib::CacheParam<
    vespalib::LruParam<IPostingListCache::BitVectorKey, std::shared_ptr<BitVector>>,
    const PostingListCache::BackingStore,
    vespalib::zero<IPostingListCache::BitVectorKey>,
    BitVectorCacheValueSize
>;

class PostingListCache::BitVectorCache : public vespalib::cache<BitVectorCacheParams> {
public:
    using Parent = vespalib::cache<BitVectorCacheParams>;
    BitVectorCache(BackingStore& backing_store, size_t max_bytes);
    ~BitVectorCache();
    static size_t element_size() { return sizeof(value_type); }
};

PostingListCache::BitVectorCache::BitVectorCache(BackingStore& backing_store, size_t max_bytes)
    : Parent(backing_store, max_bytes)
{
}

PostingListCache::BitVectorCache::~BitVectorCache() = default;

PostingListCache::PostingListCache(size_t max_bytes, size_t bitvector_max_bytes)
    : IPostingListCache(),
      _backing_store(std::make_unique<BackingStore>()),
      _cache(std::make_unique<Cache>(*_backing_store, max_bytes)),
      _bitvector_cache(std::make_unique<BitVectorCache>(*_backing_store, bitvector_max_bytes))
{
}

PostingListCache::~PostingListCache() = default;

PostingListHandle
PostingListCache::read(const Key& key, Context& ctx) const
{
    return _cache->read(key, ctx);
}

std::shared_ptr<BitVector>
PostingListCache::read(const BitVectorKey& key, Context& ctx) const
{
    return _bitvector_cache->read(key, ctx);
}

vespalib::CacheStats
PostingListCache::get_stats() const
{
    return _cache->get_stats();
}

vespalib::CacheStats
PostingListCache::get_bitvector_stats() const
{
    return _bitvector_cache->get_stats();
}

bool
PostingListCache::enabled_for_posting_lists() const noexcept
{
    return _cache->capacityBytes() != 0;
}

bool
PostingListCache::enabled_for_bitvectors() const noexcept
{
    return _bitvector_cache->capacityBytes() != 0;
}

size_t
PostingListCache::element_size()
{
    return PostingListCache::Cache::element_size();
}

size_t
PostingListCache::bitvector_element_size()
{
    return PostingListCache::BitVectorCache::element_size();
}

}
