// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_small_numeric_search_context.h"
#include "attributeiterators.hpp"
#include <vespa/searchlib/queryeval/emptysearch.h>

namespace search::attribute {

SingleSmallNumericSearchContext::SingleSmallNumericSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const AttributeVector& toBeSearched, const Word* word_data, Word value_mask, uint32_t value_shift_shift, uint32_t value_shift_mask, uint32_t word_shift, uint32_t docid_limit)
    : NumericSearchContext<NumericRangeMatcher<T>>(toBeSearched, *qTerm, false),
      _wordData(word_data),
      _valueMask(value_mask),
      _valueShiftShift(value_shift_shift),
      _valueShiftMask(value_shift_mask),
      _wordShift(word_shift),
      _docid_limit(docid_limit)
{
}

std::unique_ptr<queryeval::SearchIterator>
SingleSmallNumericSearchContext::createFilterIterator(fef::TermFieldMatchData* matchData, bool strict)
{
    if (!valid()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    if (getIsFilter()) {
        return strict
            ? std::make_unique<FilterAttributeIteratorStrict<SingleSmallNumericSearchContext>>(*this, matchData)
            : std::make_unique<FilterAttributeIteratorT<SingleSmallNumericSearchContext>>(*this, matchData);
    }
    return strict
        ? std::make_unique<AttributeIteratorStrict<SingleSmallNumericSearchContext>>(*this, matchData)
        : std::make_unique<AttributeIteratorT<SingleSmallNumericSearchContext>>(*this, matchData);
}

uint32_t
SingleSmallNumericSearchContext::get_committed_docid_limit() const noexcept
{
    return _docid_limit;
}

}
