// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketdb.h"
#include "remove_batch_entry.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cassert>
#include <algorithm>
#include <optional>

using document::GlobalId;
using storage::spi::BucketChecksum;

namespace proton {

using bucketdb::RemoveBatchEntry;


BucketDB::BucketDB()
    : _map(),
      _cachedBucketId(),
      _cachedBucketState()
{
}

BucketDB::~BucketDB()
{
    checkEmpty();
    clear();
}

void
BucketDB::add(BucketId bucketId, const BucketState & state) {
    _map[bucketId] += state;
}

bucketdb::BucketState *
BucketDB::getBucketStatePtr(BucketId bucket)
{
    auto it(_map.find(bucket));
    return (it != _map.end()) ? &it->second : nullptr;
}

void
BucketDB::unloadBucket(BucketId bucket, const BucketState &delta)
{
    BucketState *state = getBucketStatePtr(bucket);
    assert(state);
    *state -= delta;
}

const bucketdb::BucketState &
BucketDB::add(const GlobalId &gid,
              BucketId bucketId, Timestamp timestamp, uint32_t docSize,
              SubDbType subDbType)
{
    BucketState &state = _map[bucketId];
    state.add(gid, timestamp, docSize, subDbType);
    return state;
}

void
BucketDB::remove(const GlobalId &gid,
                 BucketId bucketId, Timestamp timestamp, uint32_t docSize,
                 SubDbType subDbType)
{
    BucketState &state = _map[bucketId];
    state.remove(gid, timestamp, docSize, subDbType);
}

void
BucketDB::remove_batch(const std::vector<RemoveBatchEntry> &removed, SubDbType sub_db_type)
{
    std::optional<BucketId> prev_bucket_id;
    BucketState* state = nullptr;
    for (auto &entry : removed) {
        if (!prev_bucket_id.has_value() || prev_bucket_id.value() != entry.get_bucket_id()) {
            state = &_map[entry.get_bucket_id()];
            prev_bucket_id = entry.get_bucket_id();
        }
        state->remove(entry.get_gid(), entry.get_timestamp(), entry.get_doc_size(), sub_db_type);
    }
}

void
BucketDB::modify(const GlobalId &gid,
                 BucketId oldBucketId, Timestamp oldTimestamp, uint32_t oldDocSize,
                 BucketId newBucketId, Timestamp newTimestamp, uint32_t newDocSize,
                 SubDbType subDbType)
{
    if (oldBucketId == newBucketId) {
        BucketState &state = _map[oldBucketId];
        state.modify(gid, oldTimestamp, oldDocSize, newTimestamp, newDocSize, subDbType);
    } else {
        remove(gid, oldBucketId, oldTimestamp, oldDocSize, subDbType);
        add(gid, newBucketId, newTimestamp, newDocSize, subDbType);
    }
}

bucketdb::BucketState
BucketDB::get(BucketId bucketId) const
{
    auto itr = _map.find(bucketId);
    return (itr != _map.end()) ? itr->second : BucketState();
}

void
BucketDB::cacheBucket(BucketId bucketId)
{
    _cachedBucketId = bucketId;
    _cachedBucketState = get(bucketId);
}

void
BucketDB::uncacheBucket()
{
    _cachedBucketId = BucketId();
    _cachedBucketState = BucketState();
}

bool
BucketDB::isCachedBucket(BucketId bucketId) const
{
    return _cachedBucketId == bucketId;
}

bucketdb::BucketState
BucketDB::cachedGet(BucketId bucketId) const
{
    if (isCachedBucket(bucketId)) {
        return _cachedBucketState;
    }
    return get(bucketId);
}

storage::spi::BucketInfo
BucketDB::cachedGetBucketInfo(BucketId bucketId) const
{
    if (isCachedBucket(bucketId)) {
        return _cachedBucketState;
    }
    return get(bucketId);
}

bool
BucketDB::hasBucket(BucketId bucketId) const
{
    return (_map.find(bucketId) != _map.end());
}


bool
BucketDB::isActiveBucket(BucketId bucketId) const
{
    auto itr = _map.find(bucketId);
    return (itr != _map.end()) && itr->second.isActive();
}

document::BucketId::List
BucketDB::getBuckets() const
{
    BucketId::List buckets;
    buckets.reserve(_map.size());
    for (const auto & entry : _map) {
        buckets.push_back(entry.first);
    }
    std::sort(buckets.begin(), buckets.end());
    return buckets;
}

bool
BucketDB::empty() const
{
    return _map.empty();
}

void
BucketDB::clear()
{
    _map.clear();
}

void
BucketDB::checkEmpty() const
{
    for (auto &entry : _map) {
        const BucketState &state = entry.second;
        assert(state.empty());
        (void) state;
    }
}


void
BucketDB::setBucketState(BucketId bucketId, bool active)
{
    BucketState &state = _map[bucketId];
    state.setActive(active);
}


void
BucketDB::createBucket(BucketId bucketId)
{
    BucketState &state = _map[bucketId];
    (void) state;
}


void
BucketDB::deleteEmptyBucket(BucketId bucketId)
{
    auto itr = _map.find(bucketId);
    if (itr == _map.end()) {
        return;
    }
    const BucketState &state = itr->second;
    if (state.empty()) {
        _map.erase(itr);
    }
}

document::BucketId::List
BucketDB::getActiveBuckets() const
{
    BucketId::List buckets;
    buckets.reserve(_map.size());
    for (const auto & entry : _map) {
        if (entry.second.isActive()) {
            buckets.push_back(entry.first);
        }
    }
    std::sort(buckets.begin(), buckets.end());
    return buckets;
}

document::BucketId::List
BucketDB::populateActiveBuckets(BucketId::List buckets)
{
    BucketId::List toAdd;
    BucketId::List fixupBuckets;
    std::sort(buckets.begin(), buckets.end());
    auto si = buckets.begin();
    auto se = buckets.end();
    BucketId::List currentBuckets = getBuckets();
    for (BucketId bucketId : currentBuckets) {
        for (; si != se && !(bucketId < *si); ++si) {
            if (*si < bucketId) {
                toAdd.push_back(*si);
            } else if (!isActiveBucket(bucketId)) {
                fixupBuckets.push_back(*si);
                setBucketState(*si, true);
            }
        }
    }
    for (; si != se; ++si) {
        toAdd.push_back(*si);
    }
    BucketState activeState;
    activeState.setActive(true);
    for (BucketId  bucketId : toAdd) {
        auto [itr, inserted] = _map.insert(std::make_pair(bucketId, activeState));
        assert(inserted);
    }
    return fixupBuckets;
}

}
