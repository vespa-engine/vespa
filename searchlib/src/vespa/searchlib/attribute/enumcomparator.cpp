// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumcomparator.h"
#include <vespa/searchlib/util/foldedstringcompare.h>

namespace search {

namespace {

FoldedStringCompare _strCmp;

}

template <typename EntryT>
EnumStoreComparator<EntryT>::EnumStoreComparator(const DataStoreType& data_store, const EntryT& fallback_value, bool prefix)
    : ParentType(data_store, fallback_value)
{
    (void) prefix;
}

template <typename EntryT>
EnumStoreComparator<EntryT>::EnumStoreComparator(const DataStoreType& data_store)
    : ParentType(data_store)
{
}

template <typename EntryT>
bool
EnumStoreComparator<EntryT>::equal_helper(const EntryT& lhs, const EntryT& rhs)
{
    return vespalib::datastore::UniqueStoreComparatorHelper<EntryT>::equal(lhs, rhs);
}

EnumStoreStringComparator::EnumStoreStringComparator(const DataStoreType& data_store)
    : ParentType(data_store, nullptr)
{
}

EnumStoreStringComparator::EnumStoreStringComparator(const DataStoreType& data_store, const char* fallback_value)
    : ParentType(data_store, fallback_value)
{
}

EnumStoreFoldedStringComparator::EnumStoreFoldedStringComparator(const DataStoreType& data_store, bool prefix)
    : ParentType(data_store, nullptr),
      _prefix(prefix),
      _prefix_len(0u)
{
}

EnumStoreFoldedStringComparator::EnumStoreFoldedStringComparator(const DataStoreType& data_store,
                                                                 const char* fallback_value, bool prefix)
    : ParentType(data_store, fallback_value),
      _prefix(prefix),
      _prefix_len(0u)
{
    if (use_prefix()) {
        _prefix_len = _strCmp.size(fallback_value);
    }
}

int
EnumStoreStringComparator::compare(const char* lhs, const char* rhs)
{
    return _strCmp.compare(lhs, rhs);
}

int
EnumStoreFoldedStringComparator::compare_folded(const char* lhs, const char* rhs)
{
    return _strCmp.compareFolded(lhs, rhs);
}

int
EnumStoreFoldedStringComparator::compare_folded_prefix(const char* lhs,
                                                       const char* rhs,
                                                       size_t prefix_len)
{
    return _strCmp.compareFoldedPrefix(lhs, rhs, prefix_len);
}

template class EnumStoreComparator<int8_t>;
template class EnumStoreComparator<int16_t>;
template class EnumStoreComparator<int32_t>;
template class EnumStoreComparator<int64_t>;
template class EnumStoreComparator<float>;
template class EnumStoreComparator<double>;

}

