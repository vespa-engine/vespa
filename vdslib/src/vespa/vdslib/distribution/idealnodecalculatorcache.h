// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * A cache for an ideal nodes implementation.
 *
 * The cache is localized for quick, localized access.
 *   - There is only one spot one request can be cached, so one can quickly
 *     look whether there is a cache entry on that spot.
 *   - Use LSB bits of bucket to lookup entry such that localized entries use
 *     separate cache spots.
 *
 *
 * Making it cheap for localized
 * access, regardless of real implementation. Basically, uses LSB bits for
 * buckets, as these are the bits that differ on localized access.
 */
#pragma once

#include <vespa/vdslib/container/lruorder.h>
#include <vespa/vdslib/distribution/idealnodecalculator.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/util/linkedptr.h>

namespace storage {
namespace lib {

class IdealNodeCalculatorCache : public IdealNodeCalculatorConfigurable {
    typedef document::BucketId BucketId;

    /** Cache for all buckets for one given type (same upstate and nodetypes) */
    class TypeCache {
        struct Entry {
            IdealNodeList _result;
            LruOrder<BucketId, TypeCache>::EntryRef _order;
        };
        typedef vespalib::hash_map<BucketId, Entry, BucketId::hash> EntryMap;

        const IdealNodeCalculator& _calc;
        const NodeType& _nodeType;
        UpStates _upStates;
        LruOrder<BucketId, TypeCache> _order;
        EntryMap _entries;
        uint32_t _hitCount;
        uint32_t _missCount;
    public:
        typedef vespalib::LinkedPtr<TypeCache> LP;

        TypeCache(const IdealNodeCalculator& c, const NodeType& t, UpStates us, uint32_t size)
            : _calc(c), _nodeType(t), _upStates(us), _order(size, *this),
              _hitCount(0), _missCount(0)
        { }

        IdealNodeList get(const document::BucketId& bucket) {
            EntryMap::const_iterator it(_entries.find(bucket));
            if (it == _entries.end()) {
                ++_missCount;
                Entry& newEntry(_entries[bucket]);
                newEntry._result = _calc.getIdealNodes(_nodeType, bucket, _upStates);
                LruOrder<BucketId, TypeCache>::EntryRef tmpOrder = _order.add(bucket);
                Entry& movedEntry(_entries[bucket]); // The entry might very well move after the previous add.
                movedEntry._order = tmpOrder;
                return movedEntry._result;
            } else {
                ++_hitCount;
                _order.moveToStart(it->second._order);
                return it->second._result;
            }
        }

        void removedFromOrder(const BucketId& bucket) {
            _entries.erase(bucket);
        }

        void clearCache() {
            _entries.clear();
            _order.clear();
        }

        uint32_t getHitCount() const { return _hitCount; }
        uint32_t getMissCount() const { return _missCount; }
        void clearCounts() {
            _hitCount = 0;
            _missCount = 0;
        }
    };
    IdealNodeCalculatorConfigurable::SP _calculator;
    std::vector<TypeCache::LP> _cache;

public:
    IdealNodeCalculatorCache(IdealNodeCalculatorConfigurable::SP calc,
                             uint32_t cacheSizePerUpTypeCache)
        : _calculator(calc)
    {
        initCache(cacheSizePerUpTypeCache, *calc);
    }

    virtual void setDistribution(const Distribution& d) {
        clearCache();
        _calculator->setDistribution(d);
    }

    virtual void setClusterState(const ClusterState& cs) {
        clearCache();
        _calculator->setClusterState(cs);
    }

    virtual IdealNodeList getIdealNodes(const NodeType& nodeType,
                                        const document::BucketId& bucket,
                                        UpStates upStates) const
    {
        uint16_t requestType(getCacheType(nodeType, upStates));
        return _cache[requestType]->get(bucket);
    }

    uint32_t getHitCount() const {
        uint32_t count = 0;
        for (uint32_t i=0; i<_cache.size(); ++i) {
            count += _cache[i]->getHitCount();
        }
        return count;
    }

    uint32_t getMissCount() const {
        uint32_t count = 0;
        for (uint32_t i=0; i<_cache.size(); ++i) {
            count += _cache[i]->getMissCount();
        }
        return count;
    }
    
    void clearCounts() {
        for (uint32_t i=0; i<_cache.size(); ++i) {
            _cache[i]->clearCounts();
        }
    }

private:
    void clearCache() {
        for (size_t i=0; i<_cache.size(); ++i) {
            _cache[i]->clearCache();
        }
    }

    void initCache(uint32_t size, IdealNodeCalculator& calc) {
        _cache.resize(2 * UP_STATE_COUNT);
        for (uint32_t i=0; i<2; ++i) {
            const NodeType& nt(i == 0 ? NodeType::DISTRIBUTOR
                                      : NodeType::STORAGE);
            for (uint32_t j=0; j<UP_STATE_COUNT; ++j) {
                UpStates upStates = (UpStates) j;
                uint16_t type = getCacheType(nt, upStates);
                _cache[type].reset(new TypeCache(calc, nt, upStates, size));
            }
        }
    }

    static uint16_t getCacheType(const NodeType& nt, UpStates s) {
        uint16_t typeEnum = nt;
        return (s << 1) | typeEnum;
    }
};

} // lib
} // storage
