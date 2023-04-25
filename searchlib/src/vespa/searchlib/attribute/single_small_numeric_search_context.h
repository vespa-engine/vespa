// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "numeric_search_context.h"
#include "numeric_range_matcher.h"
#include <vespa/vespalib/util/atomic.h>

namespace search::attribute {

/*
 * SingleSmallNumericSearchContext handles the creation of search iterators for
 * a query term on a single value small numeric attribute vector.
 */
class SingleSmallNumericSearchContext : public NumericSearchContext<NumericRangeMatcher<int8_t>>
{
private:
    using Word = uint32_t;
    using T = int8_t;
    const Word *_wordData;
    Word _valueMask;
    uint32_t _valueShiftShift;
    uint32_t _valueShiftMask;
    uint32_t _wordShift;
    uint32_t _docid_limit;

    int32_t onFind(DocId docId, int32_t elementId, int32_t & weight) const override {
        return find(docId, elementId, weight);
    }

    int32_t onFind(DocId docId, int32_t elementId) const override {
        return find(docId, elementId);
    }

public:
    SingleSmallNumericSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const AttributeVector& toBeSearched, const Word* word_data, Word value_mask, uint32_t value_shift_shift, uint32_t value_shift_mask, uint32_t word_shift, uint32_t docid_limit);

    int32_t find(DocId docId, int32_t elemId, int32_t & weight) const {
        if ( elemId != 0) return -1;
        const Word &word = _wordData[docId >> _wordShift];
        uint32_t valueShift = (docId & _valueShiftMask) << _valueShiftShift;
        T v = (vespalib::atomic::load_ref_relaxed(word) >> valueShift) & _valueMask;
        weight = 1;
        return match(v) ? 0 : -1;
    }

    int32_t find(DocId docId, int32_t elemId) const {
        if ( elemId != 0) return -1;
        const Word &word = _wordData[docId >> _wordShift];
        uint32_t valueShift = (docId & _valueShiftMask) << _valueShiftShift;
        T v = (vespalib::atomic::load_ref_relaxed(word) >> valueShift) & _valueMask;
        return match(v) ? 0 : -1;
    }

    std::unique_ptr<queryeval::SearchIterator>
    createFilterIterator(fef::TermFieldMatchData* matchData, bool strict) override;
    uint32_t get_committed_docid_limit() const noexcept override;
};

}
