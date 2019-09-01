// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enum_store_dictionary.h"
#include "i_enum_store.h"
#include <vespa/searchlib/util/foldedstringcompare.h>
#include <vespa/vespalib/btree/btreenode.h>
#include <vespa/vespalib/btree/btreenodeallocator.h>
#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/btree/btreebuilder.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cmath>

namespace search {

template <typename> class EnumStoreComparatorT;
template <typename> class EnumStoreFoldedComparatorT;

/**
 * Class representing a numeric entry type in a enum store.
 * Used as template argument for EnumStoreT.
 **/

template <typename T>
class NumericEntryType {
public:
    typedef T Type;
    static uint32_t size(Type)  { return fixedSize(); }
    static uint32_t fixedSize() { return sizeof(T); }
    static bool hasFold() { return false; }
};

/**
 * Class representing a string entry type in a enum store.
 * Used as template argument for EnumStoreT.
 **/
class StringEntryType {
public:
    typedef const char * Type;
    static uint32_t size(Type value) { return strlen(value) + fixedSize(); }
    static uint32_t fixedSize()      { return 1; }
    static bool hasFold() { return true; }
};


/**
 * Used to determine the ordering between two floating point values that can be NAN.
 **/
struct FloatingPointCompareHelper
{
    template <typename T>
    static int compare(T a, T b) {
        if (std::isnan(a) && std::isnan(b)) {
            return 0;
        } else if (std::isnan(a)) {
            return -1;
        } else if (std::isnan(b)) {
            return 1;
        } else if (a < b) {
            return -1;
        } else if (a == b) {
            return 0;
        }
        return 1;
    }
};


//-----------------------------------------------------------------------------
// EnumStoreT
//-----------------------------------------------------------------------------
template <class EntryType>
class EnumStoreT : public IEnumStore
{
    friend class EnumStoreTest;
public:
    using Type = typename EntryType::Type;
    using ComparatorType = EnumStoreComparatorT<EntryType>;
    using FoldedComparatorType = EnumStoreFoldedComparatorT<EntryType>;
    using EnumStoreType = EnumStoreT<EntryType>;
    using DataStoreType = datastore::DataStoreT<Index>;
    using generation_t = vespalib::GenerationHandler::generation_t;

    class EntryBase {
    protected:
        char * _data;
    public:
        EntryBase(void * data) : _data(static_cast<char *>(data)) {}
        uint32_t getRefCount() const {
            return *(reinterpret_cast<uint32_t *>(_data) + 1);
        }
        void incRefCount() {
            uint32_t *dst = reinterpret_cast<uint32_t *>(_data) + 1;
            ++(*dst);
        }
        void decRefCount() {
            uint32_t *dst = reinterpret_cast<uint32_t *>(_data) + 1;
            --(*dst);
        }
        void setRefCount(uint32_t refCount) {
            uint32_t *dst = reinterpret_cast<uint32_t *>(_data) + 1;
            *dst = refCount;
        }
        static uint32_t size() { return 2*sizeof(uint32_t); }
    };

    class Entry : public EntryBase {
    public:
        Entry(void * data) : EntryBase(data) {}
        Type getValue() const;
        static uint32_t fixedSize() { return EntryBase::size() + EntryType::fixedSize(); }
    };

    class EnumBufferType : public datastore::BufferType<char> {
    private:
        size_t _minSizeNeeded; // lower cap for sizeNeeded
        size_t _deadElems;     // dead elements in active buffer
        bool   _pendingCompact;
        bool   _wantCompact;
    public:
        EnumBufferType();
        size_t calcArraysToAlloc(uint32_t bufferId, size_t sizeNeeded, bool resizing) const override;
        void setSizeNeededAndDead(size_t sizeNeeded, size_t deadElems) {
            _minSizeNeeded = sizeNeeded;
            _deadElems = deadElems;
        }
        void onFree(size_t usedElems) override {
            datastore::BufferType<char>::onFree(usedElems);
            _pendingCompact = _wantCompact;
            _wantCompact = false;
        }
        void setWantCompact() { _wantCompact = true; }
        bool getPendingCompact() const { return _pendingCompact; }
        void clearPendingCompact() { _pendingCompact = false; }
    };

    static void insertEntry(char * dst, uint32_t refCount, Type value);

private:
    IEnumStoreDictionary *_enumDict;
    DataStoreType         _store;
    EnumBufferType        _type;
    std::vector<uint32_t> _toHoldBuffers; // used during compaction

    static const uint32_t TYPE_ID = 0;

    EnumStoreT(const EnumStoreT & rhs) = delete;
    EnumStoreT & operator=(const EnumStoreT & rhs) = delete;

    static void insertEntryValue(char * dst, Type value) {
        memcpy(dst, &value, sizeof(Type));
    }

    EntryBase getEntryBase(Index idx) const {
        return EntryBase(const_cast<DataStoreType &>(_store).getEntry<char>(idx));
    }
    datastore::BufferState & getBuffer(uint32_t bufferIdx) {
        return _store.getBufferState(bufferIdx);
    }
    const datastore::BufferState & getBuffer(uint32_t bufferIdx) const {
        return _store.getBufferState(bufferIdx);
    }
    bool validIndex(Index idx) const {
        return (idx.valid() && idx.offset() < _store.getBufferState(idx.bufferId()).size());
    }
    uint32_t getBufferIndex(datastore::BufferState::State status);
    void postCompact();
    bool preCompact(uint64_t bytesNeeded);

    Entry getEntry(Index idx) const {
        return Entry(const_cast<DataStoreType &>(_store).getEntry<char>(idx));
    }

    void freeUnusedEnum(Index idx, IndexSet & unused) override;

public:
    EnumStoreT(uint64_t initBufferSize, bool hasPostings);
    virtual ~EnumStoreT();

    void reset(uint64_t initBufferSize);

    uint32_t getRefCount(Index idx) const { return getEntryBase(idx).getRefCount(); }
    void incRefCount(Index idx)           { getEntryBase(idx).incRefCount(); }
    void decRefCount(Index idx)           { getEntryBase(idx).decRefCount(); }

    // Only use when reading from enumerated attribute save files
    void fixupRefCount(Index idx, uint32_t refCount) override {
        getEntryBase(idx).setRefCount(refCount);
    }

    uint32_t getNumUniques() const override { return _enumDict->getNumUniques(); }

    uint32_t getRemaining() const {
        return _store.getBufferState(_store.getActiveBufferId(TYPE_ID)).remaining();
    }
    uint32_t getCapacity() const {
        return _store.getBufferState(_store.getActiveBufferId(TYPE_ID)).capacity();
    }
    vespalib::MemoryUsage getMemoryUsage() const override { return _store.getMemoryUsage(); }
    vespalib::MemoryUsage getTreeMemoryUsage() const override { return _enumDict->get_memory_usage(); }

    vespalib::AddressSpace getAddressSpaceUsage() const;

    void transferHoldLists(generation_t generation);
    void trimHoldLists(generation_t firstUsed);

    static void failNewSize(uint64_t minNewSize, uint64_t maxSize);

    // Align buffers and entries to 4 bytes boundary.
    static uint64_t alignBufferSize(uint64_t val) { return Index::align(val); }
    static uint32_t alignEntrySize(uint32_t val) { return Index::align(val); }

    void fallbackResize(uint64_t bytesNeeded);
    bool getPendingCompact() const { return _type.getPendingCompact(); }
    void clearPendingCompact() { _type.clearPendingCompact(); }

    ssize_t deserialize0(const void *src, size_t available, IndexVector &idx) override;

    ssize_t deserialize(const void *src, size_t available, IndexVector &idx) {
        return _enumDict->deserialize(src, available, idx);
    }

    void fixupRefCounts(const EnumVector &hist) { _enumDict->fixupRefCounts(hist); }
    void freezeTree() { _enumDict->freeze(); }

    IEnumStoreDictionary &getEnumStoreDict() override { return *_enumDict; }
    const IEnumStoreDictionary &getEnumStoreDict() const override { return *_enumDict; }
    EnumPostingTree &getPostingDictionary() { return _enumDict->getPostingDictionary(); }

    const EnumPostingTree &getPostingDictionary() const {
        return _enumDict->getPostingDictionary();
    }
    const datastore::DataStoreBase &get_data_store_base() const override { return _store; }


    bool getValue(Index idx, Type & value) const;
    Type     getValue(uint32_t idx) const { return getValue(Index(datastore::EntryRef(idx))); }
    Type     getValue(Index idx)    const { return getEntry(idx).getValue(); }

    static uint32_t getEntrySize(Type value) {
        return alignEntrySize(EntryBase::size() + EntryType::size(value));
    }

    class Builder {
    public:
        struct UniqueEntry {
            UniqueEntry(const Type & val, size_t sz, uint32_t pidx = 0) : _value(val), _sz(sz), _pidx(pidx), _refCount(1) { }
            Type     _value;
            size_t   _sz;
            size_t   _pidx;
            uint32_t _refCount;
        };

        typedef vespalib::Array<UniqueEntry> Uniques;
    private:
        Uniques _uniques;
        uint64_t _bufferSize;
    public:
        Builder();
        ~Builder();
        Index insert(Type value, uint32_t pidx = 0) {
            uint32_t entrySize = getEntrySize(value);
            _uniques.push_back(UniqueEntry(value, entrySize, pidx));
            Index index(_bufferSize, 0); // bufferId 0 should be used when resetting with a builder
            _bufferSize += entrySize;
            return index;
        }
        void updateRefCount(uint32_t refCount) { _uniques.rbegin()->_refCount = refCount; }
        const Uniques & getUniques() const { return _uniques; }
        uint64_t getBufferSize()     const { return _bufferSize; }
    };

    class BatchUpdater {
    private:
        EnumStoreType& _store;
        IndexSet _possibly_unused;

    public:
        BatchUpdater(EnumStoreType& store)
            : _store(store),
              _possibly_unused()
        {}
        void add(Type value) {
            Index new_idx;
            _store.addEnum(value, new_idx);
            _possibly_unused.insert(new_idx);
        }
        void inc_ref_count(Index idx) {
            _store.incRefCount(idx);
        }
        void dec_ref_count(Index idx) {
            _store.decRefCount(idx);
            if (_store.getRefCount(idx) == 0) {
                _possibly_unused.insert(idx);
            }
        }
        void commit() {
            _store.freeUnusedEnums(_possibly_unused);
        }
    };

    BatchUpdater make_batch_updater() {
        return BatchUpdater(*this);
    }

    void writeValues(BufferWriter &writer, const Index *idxs, size_t count) const override;
    ssize_t deserialize(const void *src, size_t available, size_t &initSpace);
    ssize_t deserialize(const void *src, size_t available, Index &idx);
    bool foldedChange(const Index &idx1, const Index &idx2) override;
    virtual bool findEnum(Type value, IEnumStore::EnumHandle &e) const;
    virtual std::vector<IEnumStore::EnumHandle> findFoldedEnums(Type value) const;
    void addEnum(Type value, Index &newIdx);
    virtual bool findIndex(Type value, Index &idx) const;
    void freeUnusedEnums(bool movePostingidx) override;
    void freeUnusedEnums(const IndexSet& toRemove);
    void reset(Builder &builder);
    bool performCompaction(uint64_t bytesNeeded, EnumIndexMap & old2New);

private:
    template <typename Dictionary>
    void reset(Builder &builder, Dictionary &dict);

    template <typename Dictionary>
    void addEnum(Type value, Index &newIdx, Dictionary &dict);

    template <typename Dictionary>
    void performCompaction(Dictionary &dict, EnumIndexMap & old2New);
};

vespalib::asciistream & operator << (vespalib::asciistream & os, const IEnumStore::Index & idx);

extern template
class datastore::DataStoreT<IEnumStore::Index>;


template <typename EntryType>
inline typename EntryType::Type
EnumStoreT<EntryType>::Entry::getValue() const // implementation for numeric
{
    Type dst;
    const char * src = this->_data + EntryBase::size();
    memcpy(&dst, src, sizeof(Type));
    return dst;
}

template <>
inline StringEntryType::Type
EnumStoreT<StringEntryType>::Entry::getValue() const
{
    return (_data + EntryBase::size());
}


template <>
void
EnumStoreT<StringEntryType>::writeValues(BufferWriter &writer,
                                         const Index *idxs,
                                         size_t count) const;

template <>
ssize_t
EnumStoreT<StringEntryType>::deserialize(const void *src,
                                            size_t available,
                                            size_t &initSpace);

template <>
ssize_t
EnumStoreT<StringEntryType>::deserialize(const void *src,
                                            size_t available,
                                            Index &idx);


//-----------------------------------------------------------------------------
// EnumStore
//-----------------------------------------------------------------------------

template <>
void
EnumStoreT<StringEntryType>::
insertEntryValue(char * dst, Type value);


extern template
class btree::BTreeBuilder<IEnumStore::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                          EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;
extern template
class btree::BTreeBuilder<IEnumStore::Index, datastore::EntryRef, btree::NoAggregated,
                          EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template class EnumStoreT< StringEntryType >;
extern template class EnumStoreT<NumericEntryType<int8_t> >;
extern template class EnumStoreT<NumericEntryType<int16_t> >;
extern template class EnumStoreT<NumericEntryType<int32_t> >;
extern template class EnumStoreT<NumericEntryType<int64_t> >;
extern template class EnumStoreT<NumericEntryType<float> >;
extern template class EnumStoreT<NumericEntryType<double> >;

} // namespace search

