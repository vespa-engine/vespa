// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_enum_search_context.h"
#include "numeric_range_matcher.h"
#include "numeric_search_context.h"

namespace search::attribute {

/*
 * MultiNumericEnumSearchContext handles the creation of search iterators for
 * a query term on a multi value numeric enumerated attribute vector.
 */
template <typename T, typename M>
class MultiNumericEnumSearchContext : public MultiEnumSearchContext<T, NumericSearchContext<NumericRangeMatcher<T>>, M>
{
public:
    MultiNumericEnumSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const AttributeVector& toBeSearched, MultiValueMappingReadView<M> mv_mapping_read_view, const EnumStoreT<T>& enum_store);
};

}
