// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_string_enum_search_context.h"
#include "enumhintsearchcontext.h"

namespace search::attribute {

/*
 * MultiStringEnumHintSearchContext handles the creation of search iterators
 * for a query term on a multi value string enumerated attribute vector using
 * dictionary information to eliminate searches for nonexisting words.
 */
template <typename M>
class MultiStringEnumHintSearchContext : public MultiStringEnumSearchContext<M>,
                                         public EnumHintSearchContext
{
public:
    MultiStringEnumHintSearchContext(std::unique_ptr<QueryTermSimple> qTerm, bool cased, const AttributeVector& toBeSearched, MultiValueMappingReadView<M> mv_mapping_read_view, const EnumStoreT<const char*>& enum_store, uint32_t doc_id_limit, uint64_t num_values);
    ~MultiStringEnumHintSearchContext() override;
};

}
