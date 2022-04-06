// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_enum_search_context.h"
#include "string_search_context.h"

namespace search::attribute {

/*
 * MultiStringEnumSearchContext handles the creation of search iterators for
 * a query term on a multi value string enumerated attribute vector.
 */
template <typename M>
class MultiStringEnumSearchContext : public MultiEnumSearchContext<const char*, StringSearchContext, M>
{
public:
    MultiStringEnumSearchContext(std::unique_ptr<QueryTermSimple> qTerm, bool cased, const AttributeVector& toBeSearched, MultiValueMappingReadView<M> mv_mapping_read_view, const EnumStoreT<const char*>& enum_store);
};

}
