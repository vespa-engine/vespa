// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "single_numeric_enum_search_context.h"
#include "single_enum_search_context.hpp"

namespace search::attribute {

template <typename T>
SingleNumericEnumSearchContext<T>::SingleNumericEnumSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const AttributeVector& toBeSearched, const vespalib::datastore::AtomicEntryRef* enum_indices, const EnumStoreT<T>& enum_store)
    : SingleEnumSearchContext<T, NumericSearchContext<NumericRangeMatcher<T>>>(NumericRangeMatcher<T>(*qTerm, true), toBeSearched, enum_indices, enum_store)
{
}

}
