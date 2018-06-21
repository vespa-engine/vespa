// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumcomparator.h"
#include "enumstore.hpp"
#include <vespa/searchlib/util/foldedstringcompare.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.enum_comparator");

namespace search {

namespace {

FoldedStringCompare _strCmp;

}

template <>
int
EnumStoreComparatorT<NumericEntryType<float> >::compare(EntryValue lhs, EntryValue rhs)
{
    return FloatingPointCompareHelper::compare(lhs, rhs);
}

template <>
int
EnumStoreComparatorT<NumericEntryType<double> >::compare(EntryValue lhs, EntryValue rhs)
{
    return FloatingPointCompareHelper::compare(lhs, rhs);
}

template <>
EnumStoreFoldedComparatorT<StringEntryType>::
EnumStoreFoldedComparatorT(const EnumStoreType & enumStore,
                           EntryValue value, bool prefix)
    : ParentType(enumStore, value),
      _prefix(prefix),
      _prefixLen(0u)
{
    if (getUsePrefix())
        _prefixLen = _strCmp.size(value);
}

template <>
int
EnumStoreComparatorT<StringEntryType>::compare(EntryValue lhs, EntryValue rhs)
{
    return _strCmp.compare(lhs, rhs);
}

template <>
int
EnumStoreFoldedComparatorT<StringEntryType>::compareFolded(EntryValue lhs,
        EntryValue rhs)
{
    return _strCmp.compareFolded(lhs, rhs);
}

template <>
int
EnumStoreFoldedComparatorT<StringEntryType>::
compareFoldedPrefix(EntryValue lhs,
                    EntryValue rhs,
                    size_t prefixLen)
{
    return _strCmp.compareFoldedPrefix(lhs, rhs, prefixLen);
}

template class EnumStoreComparatorT<StringEntryType>;
template class EnumStoreComparatorT<NumericEntryType<int8_t> >;
template class EnumStoreComparatorT<NumericEntryType<int16_t> >;
template class EnumStoreComparatorT<NumericEntryType<int32_t> >;
template class EnumStoreComparatorT<NumericEntryType<int64_t> >;
template class EnumStoreComparatorT<NumericEntryType<float> >;
template class EnumStoreComparatorT<NumericEntryType<double> >;
template class EnumStoreFoldedComparatorT<StringEntryType>;
template class EnumStoreFoldedComparatorT<NumericEntryType<int8_t> >;
template class EnumStoreFoldedComparatorT<NumericEntryType<int16_t> >;
template class EnumStoreFoldedComparatorT<NumericEntryType<int32_t> >;
template class EnumStoreFoldedComparatorT<NumericEntryType<int64_t> >;
template class EnumStoreFoldedComparatorT<NumericEntryType<float> >;
template class EnumStoreFoldedComparatorT<NumericEntryType<double> >;

} // namespace search

