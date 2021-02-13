// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bitvectorcache.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <algorithm>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.common.bitvectorcache");

namespace search {

BitVectorCache::BitVectorCache(GenerationHolder &genHolder) :
    _lookupCount(0),
    _needPopulation(false),
    _lock(),
    _keys(),
    _chunks(),
    _genHolder(genHolder)
{
}

BitVectorCache::~BitVectorCache() = default;

void
BitVectorCache::computeCountVector(KeySet & keys, CountVector & v) const
{
    std::vector<Key> notFound;
    std::vector<CondensedBitVector::KeySet> keySets;
    ChunkV chunks;
    {
        std::lock_guard<std::mutex> guard(_lock);
        keySets.resize(_chunks.size());
        Key2Index::const_iterator end(_keys.end());
        for (Key k : keys) {
            Key2Index::const_iterator found = _keys.find(k);
            if (found != end) {
                const KeyMeta & m = found->second;
                keySets[m.chunkId()].insert(m.chunkIndex());
            }
        }
        chunks = _chunks;
    }
    for (Key k : notFound) {
        keys.erase(k);
    }
    size_t index(0);
    if (chunks.empty()) {
        memset(&v[0], 0, v.size());
    }
    for (const auto & chunk : chunks) {
        if (index == 0) {
            chunk->initializeCountVector(keySets[index++], v);
        } else {
            chunk->addCountVector(keySets[index++], v);
        }
    }
}

BitVectorCache::KeySet
BitVectorCache::lookupCachedSet(const KeyAndCountSet & keys)
{
    KeySet cached(keys.size()*3);
    std::lock_guard<std::mutex> guard(_lock);
    _lookupCount++;
    if (_lookupCount == 2000) {
        _needPopulation = true;
    } else if ((_lookupCount & 0x1fffff) == 0x100000) {
        if (hasCostChanged(guard)) {
            _needPopulation = true;
        }
    }
    for (const auto & e : keys) {
        auto found = _keys.find(e.first);
        if (found != _keys.end()) {
            KeyMeta & m = found->second;
            m.lookup();
            if (m.isCached()) {
                cached.insert(e.first);
            }
        } else {
            _keys[e.first] = KeyMeta().lookup().bitCount(e.second);
        }
    }
    return cached;
}

BitVectorCache::SortedKeyMeta
BitVectorCache::getSorted(Key2Index & keys)
{
    std::vector<std::pair<Key, KeyMeta *>> sorted;
    sorted.reserve(keys.size());
    for (auto & e : keys) {
        sorted.push_back({e.first, &e.second});
    }
    std::sort(sorted.begin(), sorted.end(),
        [&] (const auto & a, const auto & b) {
             return a.second->cost() > b.second->cost();
        });
    return sorted;
}

bool
BitVectorCache::hasCostChanged(const std::lock_guard<std::mutex> & guard)
{
    (void) guard;
    if ( ! _chunks.empty()) {
        SortedKeyMeta sorted(getSorted(_keys));
        double oldCached(0);
        for (auto & e : sorted) {
            const KeyMeta & m = *e.second;
            if ( m.isCached() ) {
                oldCached += m.cost();
            }
        }
        double newCached(0);
        for (size_t i(0); i < sorted.size() && i < _chunks[0]->getKeyCapacity(); i++) {
            const KeyMeta & m = *sorted[i].second;
            newCached += m.cost();
        }
        if (newCached > oldCached * 1.01) {  // 1% change needed.
            return true;
        }
    }
    return false;
}

void
BitVectorCache::populate(Key2Index & newKeys, CondensedBitVector & chunk, const PopulateInterface & lookup)
{
    SortedKeyMeta sorted(getSorted(newKeys));

    double sum(0);
    for (auto & e : sorted) {
        e.second->unCache();
        sum += e.second->cost();
    }
    double accum(0.0);
    uint32_t index(0);
    for (const auto & e : sorted) {
        KeyMeta & m = *e.second;
        if (index >= chunk.getKeyCapacity()) {
            assert( ! m.isCached());
        } else {
            double percentage(m.cost()*100.0/sum);
            accum += percentage;
            m.chunkId(0);
            m.chunkIndex(index);
            LOG(info, "Populating bitvector %2d with feature %" PRIu64 " and %ld bits set. Cost is %8f = %2.2f%%, accumulated cost is %2.2f%%",
                       index, e.first, m.bitCount(), m.cost(), percentage, accum);
            assert(m.isCached());
            assert(newKeys[e.first].isCached());
            assert(&m == &newKeys[e.first]);
            PopulateInterface::Iterator::UP iterator = lookup.lookup(e.first);
            if (iterator) {
                for (int32_t docId(iterator->getNext()); docId >= 0; docId = iterator->getNext()) {
                    chunk.set(m.chunkIndex(), docId, true);
                }
            } else {
                LOG(error, "Unable to to find a valid iterator for feature %" PRIu64 " and %ld bits set at while populating bitvector %2d. This should in theory be impossible.",
                           e.first, m.bitCount(), index);
            }
            index++;
        }
    }
}

void
BitVectorCache::populate(uint32_t sz, const PopulateInterface & lookup)
{
    std::unique_lock<std::mutex> guard(_lock);
    if (! _needPopulation) {
        return;
    }
    Key2Index newKeys(_keys);
    guard.unlock();

    CondensedBitVector::UP chunk(CondensedBitVector::create(sz, _genHolder));
    populate(newKeys, *chunk, lookup);

    guard.lock();
    _chunks.push_back(std::move(chunk));
    _keys.swap(newKeys);
    _needPopulation = false;
}

void
BitVectorCache::set(Key key, uint32_t index, bool v)
{
    std::lock_guard<std::mutex> guard(_lock);
    auto found = _keys.find(key);
    if (found != _keys.end()) {
        const KeyMeta & m(found->second);
        if (m.isCached()) {
            _chunks[m.chunkId()]->set(m.chunkIndex(), index, v);
        }
    }
}

bool
BitVectorCache::get(Key key, uint32_t index) const
{
    (void) key; (void) index;
    return false;
}

void
BitVectorCache::removeIndex(uint32_t index)
{
    std::lock_guard<std::mutex> guard(_lock);
    for (auto & chunk : _chunks) {
        chunk->clearIndex(index);
    }
}


void
BitVectorCache::adjustDocIdLimit(uint32_t docId)
{
    for (auto &chunk : _chunks) {
        chunk->adjustDocIdLimit(docId);
    }
}

}
