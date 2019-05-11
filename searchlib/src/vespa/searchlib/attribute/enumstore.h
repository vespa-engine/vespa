// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumstorebase.h"
#include <vespa/searchlib/util/foldedstringcompare.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cmath>
#include <vespa/searchlib/datastore/entryref.h>
#include <vespa/searchlib/btree/btreenode.h>
#include <vespa/searchlib/btree/btreenodeallocator.h>
#include <vespa/searchlib/btree/btree.h>
#include <vespa/searchlib/btree/btreebuilder.h>

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
class EnumStoreT : public EnumStoreBase
{
    friend class EnumStoreTest;
public:
    typedef typename EntryType::Type Type;
    typedef EnumStoreComparatorT<EntryType> ComparatorType;
    typedef EnumStoreFoldedComparatorT<EntryType> FoldedComparatorType;
    using EnumStoreBase::deserialize;
    using EnumStoreBase::fixupRefCounts;
    using EnumStoreBase::reset;

    class Entry : public EntryBase {
    public:
        Entry(void * data) : EntryBase(data) {}
        Type getValue() const;
        static uint32_t fixedSize() { return EntryBase::size() + EntryType::fixedSize(); }
    };
    static void insertEntry(char * dst, uint32_t enumValue, uint32_t refCount, Type value);

private:
    EnumStoreT(const EnumStoreT & rhs) = delete;
    EnumStoreT & operator=(const EnumStoreT & rhs) = delete;

    static void insertEntryValue(char * dst, Type value) {
        memcpy(dst, &value, sizeof(Type));
    }

protected:
    typedef EnumStoreBase::IndexSet IndexSet;
    using EnumStoreBase::_store;
    using EnumStoreBase::TYPE_ID;

    Entry getEntry(Index idx) const {
        return Entry(const_cast<DataStoreType &>(_store).getEntry<char>(idx));
    }
    void printEntry(vespalib::asciistream & os, const Entry & e) const;

    void freeUnusedEnum(Index idx, IndexSet & unused) override;

public:
    EnumStoreT(uint64_t initBufferSize, bool hasPostings)
        : EnumStoreBase(initBufferSize, hasPostings)
    {
    }

    bool getValue(Index idx, Type & value) const;
    Type     getValue(uint32_t idx) const { return getValue(Index(datastore::EntryRef(idx))); }
    Type     getValue(Index idx)    const { return getEntry(idx).getValue(); }
    uint32_t getFixedSize() const override { return Entry::fixedSize(); }

    static uint32_t
    getEntrySize(Type value)
    {
        return alignEntrySize(EntryBase::size() + EntryType::size(value));
    }
    void printBuffer(vespalib::asciistream & os, uint32_t bufferIdx) const;
    void printValue(vespalib::asciistream & os, Index idx) const;
    void printValue(vespalib::asciistream & os, Type value) const;

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

    void writeValues(BufferWriter &writer, const Index *idxs, size_t count) const override;
    ssize_t deserialize(const void *src, size_t available, size_t &initSpace) override;
    ssize_t deserialize(const void *src, size_t available, Index &idx) override;
    bool foldedChange(const Index &idx1, const Index &idx2) override;
    virtual bool findEnum(Type value, EnumStoreBase::EnumHandle &e) const;
    virtual std::vector<EnumStoreBase::EnumHandle> findFoldedEnums(Type value) const;
    void addEnum(Type value, Index &newIdx);
    virtual bool findIndex(Type value, Index &idx) const;
    void freeUnusedEnums(bool movePostingidx) override;
    void freeUnusedEnums(const IndexVector &toRemove) override;
    void reset(Builder &builder);
    bool performCompaction(uint64_t bytesNeeded, EnumIndexMap & old2New) override;
    void printCurrentContent(vespalib::asciistream &os) const;

private:
    template <typename Dictionary>
    void reset(Builder &builder, Dictionary &dict);

    template <typename Dictionary>
    void addEnum(Type value, Index &newIdx, Dictionary &dict);

    template <typename Dictionary>
    void performCompaction(Dictionary &dict, EnumIndexMap & old2New);

    template <typename Dictionary>
    void printCurrentContent(vespalib::asciistream &os, const Dictionary &dict) const;
};

template <typename EntryType>
inline typename EntryType::Type
EnumStoreT<EntryType>::Entry::getValue() const // implementation for numeric
{
    Type dst;
    const char * src = _data + EntryBase::size();
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

template <>
void
EnumStoreT<StringEntryType>::
printEntry(vespalib::asciistream & os, const Entry & e) const;

template <>
void
EnumStoreT<NumericEntryType<float> >::
printEntry(vespalib::asciistream & os, const Entry & e) const;

template <>
void
EnumStoreT<NumericEntryType<double> >::
printEntry(vespalib::asciistream & os, const Entry & e) const;

template <>
void
EnumStoreT<StringEntryType>::printValue(vespalib::asciistream & os, Index idx) const;

template <>
void
EnumStoreT<StringEntryType>::printValue(vespalib::asciistream & os, Type value) const;


#if 0
extern template
class btree::BTreeKeyData<EnumStoreBase::Index, btree::BTreeNoLeafData>;

extern template
class btree::BTreeKeyData<EnumStoreBase::Index, datastore::EntryRef>;

extern template
class btree::BTreeNodeT<EnumStoreBase::Index, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeNodeTT<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;
#endif


extern template
class btree::BTreeBuilder<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                          EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;
extern template
class btree::BTreeBuilder<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                          EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template class EnumStoreT< StringEntryType >;
extern template class EnumStoreT<NumericEntryType<int8_t> >;
extern template class EnumStoreT<NumericEntryType<int16_t> >;
extern template class EnumStoreT<NumericEntryType<int32_t> >;
extern template class EnumStoreT<NumericEntryType<int64_t> >;
extern template class EnumStoreT<NumericEntryType<float> >;
extern template class EnumStoreT<NumericEntryType<double> >;

} // namespace search

