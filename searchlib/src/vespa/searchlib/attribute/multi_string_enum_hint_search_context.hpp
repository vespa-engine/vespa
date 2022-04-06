// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_string_enum_hint_search_context.h"
#include <vespa/searchlib/query/query_term_ucs4.h>

namespace search::attribute {

template <typename M>
MultiStringEnumHintSearchContext<M>::MultiStringEnumHintSearchContext(std::unique_ptr<QueryTermSimple> qTerm, bool cased, const AttributeVector& toBeSearched,  MultiValueMappingReadView<M> mv_mapping_read_view, const EnumStoreT<const char*>& enum_store, uint32_t doc_id_limit, uint64_t num_values)
    : MultiStringEnumSearchContext<M>(std::move(qTerm), cased, toBeSearched, mv_mapping_read_view, enum_store),
      EnumHintSearchContext(enum_store.get_dictionary(),
                            doc_id_limit, num_values)
{
    this->setup_enum_hint_sc(enum_store, *this);
}

template <typename M>
MultiStringEnumHintSearchContext<M>::~MultiStringEnumHintSearchContext() = default;

}
