// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumcomparator.h"

namespace search {

template <typename EntryT>
bool EnumStoreComparator<EntryT>::equal_helper(const EntryT& lhs, const EntryT& rhs) noexcept {
    return vespalib::datastore::UniqueStoreComparatorHelper<EntryT>::equal(lhs, rhs);
}

template class EnumStoreComparator<int8_t>;
template class EnumStoreComparator<int16_t>;
template class EnumStoreComparator<int32_t>;
template class EnumStoreComparator<int64_t>;
template class EnumStoreComparator<float>;
template class EnumStoreComparator<double>;

} // namespace search
