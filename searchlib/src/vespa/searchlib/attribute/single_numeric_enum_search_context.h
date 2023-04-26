// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "single_enum_search_context.h"
#include "numeric_range_matcher.h"
#include "numeric_search_context.h"

namespace search::attribute {

/*
 * SingleNumericEnumSearchContext handles the creation of search iterators for
 * a query term on a single value numeric enumerated attribute vector.
 */
template <typename T>
class SingleNumericEnumSearchContext : public SingleEnumSearchContext<T, NumericSearchContext<NumericRangeMatcher<T>>>
{
public:
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using EnumIndices = vespalib::ConstArrayRef<AtomicEntryRef>;
    SingleNumericEnumSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const AttributeVector& toBeSearched, EnumIndices enum_indices, const EnumStoreT<T>& enum_store);
};

}
