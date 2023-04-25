// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_numeric_search_context.h"
#include "attributeiterators.hpp"
#include "multi_value_mapping.h"
#include <vespa/searchlib/queryeval/emptysearch.h>

namespace search::attribute {

template <typename T, typename M>
MultiNumericSearchContext<T, M>::MultiNumericSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const AttributeVector& toBeSearched, MultiValueMappingReadView<M> mv_mapping_read_view)
    : NumericSearchContext<NumericRangeMatcher<T>>(toBeSearched, *qTerm, false),
      _mv_mapping_read_view(mv_mapping_read_view)
{
}

template <typename T, typename M>
std::unique_ptr<queryeval::SearchIterator>
MultiNumericSearchContext<T, M>::createFilterIterator(fef::TermFieldMatchData* matchData, bool strict)
{
    if (!this->valid()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    if (this->getIsFilter()) {
        return strict
            ? std::make_unique<FilterAttributeIteratorStrict<MultiNumericSearchContext<T, M>>>(*this, matchData)
            : std::make_unique<FilterAttributeIteratorT<MultiNumericSearchContext<T, M>>>(*this, matchData);
    }
    return strict
        ? std::make_unique<AttributeIteratorStrict<MultiNumericSearchContext<T, M>>>(*this, matchData)
        : std::make_unique<AttributeIteratorT<MultiNumericSearchContext<T, M>>>(*this, matchData);
}

template <typename T, typename M>
uint32_t
MultiNumericSearchContext<T, M>::get_committed_docid_limit() const noexcept
{
    return _mv_mapping_read_view.get_committed_docid_limit();
}

}
