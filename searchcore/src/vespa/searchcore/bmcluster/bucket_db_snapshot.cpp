// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_db_snapshot.h"
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cassert>

using document::BucketId;
using document::BucketSpace;
using storage::spi::BucketInfo;
using storage::spi::PersistenceProvider;

namespace search::bmcluster {

BucketDbSnapshot::BucketDbSnapshot()
    : _buckets()
{
}

BucketDbSnapshot::~BucketDbSnapshot() = default;

void
BucketDbSnapshot::populate(BucketSpace bucket_space, PersistenceProvider& provider)
{
    auto bucket_list = provider.listBuckets(bucket_space);
    assert(!bucket_list.hasError());
    for (auto& id : bucket_list.getList()) {
        auto info = provider.getBucketInfo(storage::spi::Bucket(document::Bucket(bucket_space, id)));
        assert(!info.hasError());
        _buckets.insert(std::make_pair(id, info.getBucketInfo()));
    }
}

uint32_t
BucketDbSnapshot::count_new_documents(const BucketDbSnapshot &old) const
{
    uint32_t result = 0;
    for (auto& bucket_id_and_info : _buckets) {
        auto old_buckets_itr = old._buckets.find(bucket_id_and_info.first);
        const BucketInfo* old_info = (old_buckets_itr != old._buckets.end()) ? &old_buckets_itr->second : nullptr;
        auto& new_info = bucket_id_and_info.second;
        uint32_t new_doc_cnt = new_info.getDocumentCount();
        uint32_t old_doc_cnt = (old_info != nullptr) ? old_info->getDocumentCount() : 0u;
        if (new_doc_cnt > old_doc_cnt) {
            result += (new_doc_cnt - old_doc_cnt);
        }
    }
    return result;
}

void
BucketDbSnapshot::populate_bucket_id_set(BucketIdSet& buckets) const
{
    for (auto& id_and_info : _buckets) {
        buckets.insert(id_and_info.first);
    }
}

const BucketInfo*
BucketDbSnapshot::try_get_bucket_info(BucketId bucket_id) const
{
    auto buckets_itr = _buckets.find(bucket_id);
    return (buckets_itr != _buckets.end()) ? &buckets_itr->second : nullptr;
}

}

VESPALIB_HASH_MAP_INSTANTIATE_H(document::BucketId, storage::spi::BucketInfo, document::BucketId::hash);
