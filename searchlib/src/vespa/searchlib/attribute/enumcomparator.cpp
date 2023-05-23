// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumcomparator.h"
#include <vespa/searchlib/util/foldedstringcompare.h>

namespace search {

template <typename EntryT>
bool
EnumStoreComparator<EntryT>::equal_helper(const EntryT& lhs, const EntryT& rhs)
{
    return vespalib::datastore::UniqueStoreComparatorHelper<EntryT>::equal(lhs, rhs);
}

EnumStoreStringComparator::EnumStoreStringComparator(const DataStoreType& data_store, bool fold)
    : ParentType(data_store, nullptr),
      _fold(fold),
      _prefix(false),
      _prefix_len(0)
{
}

EnumStoreStringComparator::EnumStoreStringComparator(const DataStoreType& data_store, bool fold, const char* fallback_value)
    : ParentType(data_store, fallback_value),
      _fold(fold),
      _prefix(false),
      _prefix_len(0)
{
}

EnumStoreStringComparator::EnumStoreStringComparator(const DataStoreType& data_store, bool fold, const char* fallback_value, bool prefix)
    : ParentType(data_store, fallback_value),
      _fold(fold),
      _prefix(prefix),
      _prefix_len(0)
{
    if (use_prefix()) {
        _prefix_len = FoldedStringCompare::size(fallback_value);
    }
}

bool
EnumStoreStringComparator::less(const vespalib::datastore::EntryRef lhs, const vespalib::datastore::EntryRef rhs) const {
    return _fold
        ? (use_prefix()
            ? (FoldedStringCompare::compareFoldedPrefix(get(lhs), get(rhs), _prefix_len) < 0)
            : (FoldedStringCompare::compareFolded(get(lhs), get(rhs)) < 0))
        : (use_prefix()
           ? (FoldedStringCompare::comparePrefix(get(lhs), get(rhs), _prefix_len) < 0)
           : (FoldedStringCompare::compare(get(lhs), get(rhs)) < 0));

}

bool
EnumStoreStringComparator::equal(const vespalib::datastore::EntryRef lhs, const vespalib::datastore::EntryRef rhs) const {
    return _fold
        ? (FoldedStringCompare::compareFolded(get(lhs), get(rhs)) == 0)
        : (FoldedStringCompare::compare(get(lhs), get(rhs)) == 0);
}

template class EnumStoreComparator<int8_t>;
template class EnumStoreComparator<int16_t>;
template class EnumStoreComparator<int32_t>;
template class EnumStoreComparator<int64_t>;
template class EnumStoreComparator<float>;
template class EnumStoreComparator<double>;

}

