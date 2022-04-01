// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "numeric_search_context.h"
#include <vespa/vespalib/util/atomic.h>

namespace search::attribute {

/*
 * SingleNumericSearchContext handles the creation of search iterators for
 * a query term on a single value numeric attribute vector.
 */
template <typename T, typename M>
class SingleNumericSearchContext final : public NumericSearchContext<M>
{
private:
    using DocId = ISearchContext::DocId;
    const T* _data;

    int32_t onFind(DocId docId, int32_t elemId, int32_t& weight) const override {
        return find(docId, elemId, weight);
    }

    int32_t onFind(DocId docId, int elemId) const override {
        return find(docId, elemId);
    }

public:
    SingleNumericSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const AttributeVector& toBeSearched, const T* data);
    int32_t find(DocId docId, int32_t elemId, int32_t& weight) const {
        if ( elemId != 0) return -1;
        const T v = vespalib::atomic::load_ref_relaxed(_data[docId]);
        weight = 1;
        return this->match(v) ? 0 : -1;
    }

    int32_t find(DocId docId, int elemId) const {
        if ( elemId != 0) return -1;
        const T v = vespalib::atomic::load_ref_relaxed(_data[docId]);
        return this->match(v) ? 0 : -1;
    }

    std::unique_ptr<queryeval::SearchIterator>
    createFilterIterator(fef::TermFieldMatchData* matchData, bool strict) override;
};

}
