// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumstore.h"

namespace search {

/**
 * Template comparator class for the various entry types.
 **/
template <typename EntryType>
class EnumStoreComparatorT : public EnumStoreComparator {
public:
    typedef EnumStoreT<EntryType> EnumStoreType;
protected:
    typedef typename EntryType::Type EntryValue;
    const EnumStoreType     & _enumStore;
    EntryValue  _value;
    EntryValue getValue(const EnumIndex & idx) const {
        if (idx.valid()) {
            return _enumStore.getValue(idx);
        }
        return _value;
    }
public:
    /**
     * Creates a comparator using the given enum store.
     **/
    EnumStoreComparatorT(const EnumStoreType & enumStore);
    /**
     * Creates a comparator using the given enum store and that uses the
     * given value during compare if the enum index is invalid.
     **/
    EnumStoreComparatorT(const EnumStoreType & enumStore,
                         EntryValue value);

    static int compare(EntryValue lhs, EntryValue rhs) {
        if (lhs < rhs) {
            return -1;
        } else if (lhs == rhs) {
            return 0;
        }
        return 1;
    }
    bool operator() (const EnumIndex & lhs, const EnumIndex & rhs) const override {
        return compare(getValue(lhs), getValue(rhs)) < 0;
    }
};


/**
 * Template comparator class for the various entry types that uses folded compare.
 **/
template <typename EntryType>
class EnumStoreFoldedComparatorT : public EnumStoreComparatorT<EntryType> {
private:
    typedef EnumStoreComparatorT<EntryType> ParentType;
    typedef typename ParentType::EnumStoreType EnumStoreType;
    typedef typename ParentType::EnumIndex EnumIndex;
    typedef typename ParentType::EntryValue EntryValue;
    using ParentType::getValue;
    bool _prefix;
    size_t _prefixLen;
public:
    /**
     * Creates a comparator using the given enum store.
     * @param prefix whether we should perform prefix compare.
     **/
    EnumStoreFoldedComparatorT(const EnumStoreType & enumStore, bool prefix = false);
    /**
     * Creates a comparator using the given enum store and that uses the
     * given value during compare if the enum index is invalid.
     * @param prefix whether we should perform prefix compare.
     **/
    EnumStoreFoldedComparatorT(const EnumStoreType & enumStore,
                               EntryValue value, bool prefix = false);
    inline bool getUsePrefix() const { return false; }
    static int compareFolded(EntryValue lhs, EntryValue rhs) { return ParentType::compare(lhs, rhs); }
    static int compareFoldedPrefix(EntryValue lhs, EntryValue rhs, size_t prefixLen) {
        (void) prefixLen;
        return ParentType::compare(lhs, rhs);
    }

    bool operator() (const EnumIndex & lhs, const EnumIndex & rhs) const override {
        if (getUsePrefix())
            return compareFoldedPrefix(getValue(lhs),
                                       getValue(rhs), _prefixLen) < 0;
        return compareFolded(getValue(lhs), getValue(rhs)) < 0;
    }
};


template <typename EntryType>
EnumStoreComparatorT<EntryType>::EnumStoreComparatorT(const EnumStoreType & enumStore) :
    _enumStore(enumStore),
    _value()
{
}

template <typename EntryType>
EnumStoreComparatorT<EntryType>::EnumStoreComparatorT(const EnumStoreType & enumStore,
                                                      EntryValue value) :
    _enumStore(enumStore),
    _value(value)
{
}

template <>
int
EnumStoreComparatorT<NumericEntryType<float> >::compare(EntryValue lhs, EntryValue rhs);

template <>
int
EnumStoreComparatorT<NumericEntryType<double> >::compare(EntryValue lhs, EntryValue rhs);

template <>
int
EnumStoreComparatorT<StringEntryType>::compare(EntryValue lhs, EntryValue rhs);


template <typename EntryType>
EnumStoreFoldedComparatorT<EntryType>::
EnumStoreFoldedComparatorT(const EnumStoreType & enumStore, bool prefix)
    : ParentType(enumStore),
      _prefix(prefix),
      _prefixLen(0u)
{
}

template <typename EntryType>
EnumStoreFoldedComparatorT<EntryType>::
EnumStoreFoldedComparatorT(const EnumStoreType & enumStore,
                           EntryValue value, bool prefix)
    : ParentType(enumStore, value),
      _prefix(prefix),
      _prefixLen(0u)
{
}

template <>
EnumStoreFoldedComparatorT<StringEntryType>::
EnumStoreFoldedComparatorT(const EnumStoreType & enumStore,
                           EntryValue value, bool prefix);

template <>
int
EnumStoreFoldedComparatorT<StringEntryType>::compareFolded(EntryValue lhs,
        EntryValue rhs);

template <>
int
EnumStoreFoldedComparatorT<StringEntryType>::
compareFoldedPrefix(EntryValue lhs, EntryValue rhs, size_t prefixLen);

template <>
inline bool
EnumStoreFoldedComparatorT<StringEntryType>::getUsePrefix() const
{
    return _prefix;
}


extern template class EnumStoreComparatorT<StringEntryType>;
extern template class EnumStoreComparatorT<NumericEntryType<int8_t> >;
extern template class EnumStoreComparatorT<NumericEntryType<int16_t> >;
extern template class EnumStoreComparatorT<NumericEntryType<int32_t> >;
extern template class EnumStoreComparatorT<NumericEntryType<int64_t> >;
extern template class EnumStoreComparatorT<NumericEntryType<float> >;
extern template class EnumStoreComparatorT<NumericEntryType<double> >;
extern template class EnumStoreFoldedComparatorT<StringEntryType>;
extern template class EnumStoreFoldedComparatorT<NumericEntryType<int8_t> >;
extern template class EnumStoreFoldedComparatorT<NumericEntryType<int16_t> >;
extern template class EnumStoreFoldedComparatorT<NumericEntryType<int32_t> >;
extern template class EnumStoreFoldedComparatorT<NumericEntryType<int64_t> >;
extern template class EnumStoreFoldedComparatorT<NumericEntryType<float> >;
extern template class EnumStoreFoldedComparatorT<NumericEntryType<double> >;

} // namespace search

