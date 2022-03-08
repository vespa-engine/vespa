// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "condensedbitvectors.h"
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <mutex>

namespace search {

class PopulateInterface
{
public:
    class Iterator {
    public:
        typedef std::unique_ptr<Iterator> UP;
        virtual ~Iterator() { }
        virtual int32_t getNext() = 0;
    };
    virtual ~PopulateInterface() { }
    virtual Iterator::UP lookup(uint64_t key) const = 0;
};

class BitVectorCache
{
public:
    typedef uint64_t Key;
    typedef vespalib::hash_set<Key> KeySet;
    typedef std::vector<std::pair<Key, size_t>> KeyAndCountSet;
    typedef CondensedBitVector::CountVector CountVector;
    typedef vespalib::GenerationHolder GenerationHolder;

    BitVectorCache(GenerationHolder &genHolder);
    ~BitVectorCache();
    void computeCountVector(KeySet & keys, CountVector & v) const;
    KeySet lookupCachedSet(const KeyAndCountSet & keys);
    void set(Key key, uint32_t index, bool v);
    bool get(Key key, uint32_t index) const;
    void removeIndex(uint32_t index);
    void adjustDocIdLimit(uint32_t docId);
    void populate(uint32_t count, const PopulateInterface &);
    bool needPopulation() const { return _needPopulation; }
    void requirePopulation() { _needPopulation = true; }
private:
    class KeyMeta {
    public:
        KeyMeta() :
            _lookupCount(0),
            _bitCount(0),
            _chunkId(-1),
            _chunkIndex(0)
        { }
        double       cost()  const { return _bitCount * _lookupCount; }
        bool     isCached()  const { return _chunkId >= 0; }
        size_t   bitCount()  const { return _bitCount; }
        size_t chunkIndex()  const { return _chunkIndex; }
        size_t    chunkId()  const { return _chunkId; }
        size_t lookupCount() const { return _lookupCount; }
        KeyMeta & incBits() {    _bitCount++; return *this; }
        KeyMeta & decBits() {    _bitCount--; return *this; }
        KeyMeta &  lookup() { _lookupCount++; return *this; }
        KeyMeta &   bitCount(uint32_t v) {   _bitCount = v; return *this; }
        KeyMeta &    chunkId(uint32_t v) {    _chunkId = v; return *this; }
        KeyMeta & chunkIndex(uint32_t v) { _chunkIndex = v; return *this; }
        KeyMeta & unCache()              { _chunkId = -1; return *this; }
    private:
        size_t   _lookupCount;
        uint32_t _bitCount;
        int32_t  _chunkId;
        uint32_t _chunkIndex;
    };
    typedef vespalib::hash_map<Key, KeyMeta> Key2Index;
    typedef std::vector<std::pair<Key, KeyMeta *>> SortedKeyMeta;
    typedef std::vector<CondensedBitVector::SP> ChunkV;

    VESPA_DLL_LOCAL static SortedKeyMeta getSorted(Key2Index & keys);
    VESPA_DLL_LOCAL static void populate(Key2Index & newKeys, CondensedBitVector & chunk, const PopulateInterface & lookup);
    VESPA_DLL_LOCAL bool hasCostChanged(const std::lock_guard<std::mutex> &);

    uint64_t           _lookupCount;
    bool               _needPopulation;
    mutable std::mutex _lock;
    Key2Index          _keys;
    ChunkV             _chunks;
    GenerationHolder  &_genHolder;
};

}
