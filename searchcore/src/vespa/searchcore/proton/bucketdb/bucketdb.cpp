// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketdb.h"
#include "remove_batch_entry.h"
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
BucketDB::add(const BucketId &bucketId, const BucketState & state) {
    _map[bucketId] += state;
}

bucketdb::BucketState *
BucketDB::getBucketStatePtr(const BucketId &bucket)
{
    auto it(_map.find(bucket));
    if (it != _map.end()) {
        return &it->second;
    }
    return nullptr;
}

void
BucketDB::unloadBucket(const BucketId &bucket, const BucketState &delta)
{
    BucketState *state = getBucketStatePtr(bucket);
    assert(state);
    *state -= delta;
}

const bucketdb::BucketState &
BucketDB::add(const GlobalId &gid,
              const BucketId &bucketId, const Timestamp &timestamp, uint32_t docSize,
              SubDbType subDbType)
{
    BucketState &state = _map[bucketId];
    state.add(gid, timestamp, docSize, subDbType);
    return state;
}

void
BucketDB::remove(const GlobalId &gid,
                 const BucketId &bucketId, const Timestamp &timestamp, uint32_t docSize,
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
                 const BucketId &oldBucketId, const Timestamp &oldTimestamp, uint32_t oldDocSize,
                 const BucketId &newBucketId, const Timestamp &newTimestamp, uint32_t newDocSize,
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
BucketDB::get(const BucketId &bucketId) const
{
    auto itr = _map.find(bucketId);
    if (itr != _map.end()) {
        return itr->second;
    }
    return BucketState();
}

void
BucketDB::cacheBucket(const BucketId &bucketId)
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
BucketDB::isCachedBucket(const BucketId &bucketId) const
{
    return _cachedBucketId == bucketId;
}

bucketdb::BucketState
BucketDB::cachedGet(const BucketId &bucketId) const
{
    if (isCachedBucket(bucketId)) {
        return _cachedBucketState;
    }
    return get(bucketId);
}

storage::spi::BucketInfo
BucketDB::cachedGetBucketInfo(const BucketId &bucketId) const
{
    if (isCachedBucket(bucketId)) {
        return _cachedBucketState;
    }
    auto itr = _map.find(bucketId);
    if (itr != _map.end()) {
        return itr->second;
    }
    return BucketState();
}

bool
BucketDB::hasBucket(const BucketId &bucketId) const
{
    return (_map.find(bucketId) != _map.end());
}


bool
BucketDB::isActiveBucket(const BucketId &bucketId) const
{
    auto itr = _map.find(bucketId);
    return (itr != _map.end()) && itr->second.isActive();
}

void
BucketDB::getBuckets(BucketId::List &buckets) const
{
    buckets.reserve(_map.size());
    for (const auto & entry : _map) {
        buckets.push_back(entry.first);
    }
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
BucketDB::setBucketState(const BucketId &bucketId, bool active)
{
    BucketState &state = _map[bucketId];
    state.setActive(active);
}


void
BucketDB::createBucket(const BucketId &bucketId)
{
    BucketState &state = _map[bucketId];
    (void) state;
}


void
BucketDB::deleteEmptyBucket(const BucketId &bucketId)
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
    for (const auto & entry : _map) {
        if (entry.second.isActive()) {
            buckets.push_back(entry.first);
        }
    }
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
    for (const auto & entry : _map) {
        for (; si != se && !(entry.first < *si); ++si) {
            if (*si < entry.first) {
                toAdd.push_back(*si);
            } else if (!entry.second.isActive()) {
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
    for (const BucketId & bucketId : toAdd) {
        InsertResult ins(_map.emplace(bucketId, activeState));
        assert(ins.second);
    }
    return fixupBuckets;
}

}
