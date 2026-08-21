// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumhintsearchcontext.h"
#include "multi_string_enum_search_context.h"

#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>

namespace search::attribute {

/*
 * MultiStringEnumHintSearchContext handles the creation of search iterators
 * for a query term on a multi value string enumerated attribute vector using
 * dictionary information to eliminate searches for nonexisting words.
 */
template <typename M, typename Matcher>
class MultiStringEnumHintSearchContextT : public MultiStringEnumSearchContextT<M, Matcher>,
                                          public EnumHintSearchContext {
public:
    MultiStringEnumHintSearchContextT(std::unique_ptr<QueryTermSimple> qTerm, bool cased,
                                      vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm,
                                      const AttributeVector&           toBeSearched,
                                      MultiValueMappingReadView<M>     mv_mapping_read_view,
                                      const EnumStoreT<const char*>& enum_store, uint32_t doc_id_limit,
                                      uint64_t num_values);
    ~MultiStringEnumHintSearchContextT() override;
};

template <typename M>
using MultiStringEnumHintSearchContext = MultiStringEnumHintSearchContextT<M, StringMatcher>;

} // namespace search::attribute
