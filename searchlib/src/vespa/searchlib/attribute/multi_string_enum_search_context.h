// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_enum_search_context.h"
#include "string_search_context.h"

#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>

namespace search::attribute {

/*
 * MultiStringEnumSearchContext handles the creation of search iterators for
 * a query term on a multi value string enumerated attribute vector.
 */
template <typename M, typename Matcher>
class MultiStringEnumSearchContextT : public MultiEnumSearchContext<const char*, StringSearchContextT<Matcher>, M> {
public:
    MultiStringEnumSearchContextT(Matcher&& matcher, const AttributeVector& toBeSearched,
                                  MultiValueMappingReadView<M>   mv_mapping_read_view,
                                  const EnumStoreT<const char*>& enum_store);
    MultiStringEnumSearchContextT(MultiStringEnumSearchContextT&& rhs) noexcept = default;
    ~MultiStringEnumSearchContextT() override;
};

template <typename M>
using MultiStringEnumSearchContext = MultiStringEnumSearchContextT<M, StringMatcher>;

} // namespace search::attribute
