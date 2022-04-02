// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_string_enum_search_context.h"
#include "multi_enum_search_context.hpp"
#include <vespa/searchlib/query/query_term_simple.h>

namespace search::attribute {

template <typename M>
MultiStringEnumSearchContext<M>::MultiStringEnumSearchContext(std::unique_ptr<QueryTermSimple> qTerm, bool cased, const AttributeVector& toBeSearched, const MultiValueMapping<M>& mv_mapping, const EnumStoreT<const char*>& enum_store)
    : MultiEnumSearchContext<const char*, StringSearchContext, M>(StringMatcher(std::move(qTerm), cased), toBeSearched, mv_mapping, enum_store)
{
}

}
