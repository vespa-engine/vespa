// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "numeric_search_context.h"
#include "multi_value_mapping_read_view.h"
#include "numeric_range_matcher.h"
#include <vespa/searchcommon/attribute/multivalue.h>

namespace search::attribute {

/*
 * MultiNumericSearchContext handles the creation of search iterators for
 * a query term on a multi value numeric attribute vector.
 */
template <typename T, typename M>
class MultiNumericSearchContext : public NumericSearchContext<NumericRangeMatcher<T>>
{
private:
    using DocId = ISearchContext::DocId;
    MultiValueMappingReadView<M> _mv_mapping_read_view;

    int32_t onFind(DocId docId, int32_t elemId, int32_t& weight) const override final {
        return find(docId, elemId, weight);
    }

    int32_t onFind(DocId docId, int32_t elemId) const override final {
        return find(docId, elemId);
    }

public:
    MultiNumericSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const AttributeVector& toBeSearched, MultiValueMappingReadView<M> mv_mapping_read_view);
    int32_t find(DocId doc, int32_t elemId, int32_t & weight) const {
        auto values(_mv_mapping_read_view.get(doc));
        for (uint32_t i(elemId); i < values.size(); i++) {
            if (this->match(multivalue::get_value(values[i]))) {
                weight = multivalue::get_weight(values[i]);
                return i;
            }
        }
        weight = 0;
        return -1;
    }

    int32_t find(DocId doc, int32_t elemId) const {
        auto values(_mv_mapping_read_view.get(doc));
        for (uint32_t i(elemId); i < values.size(); i++) {
            if (this->match(multivalue::get_value(values[i]))) {
                return i;
            }
        }
        return -1;
    }

    std::unique_ptr<queryeval::SearchIterator>
    createFilterIterator(fef::TermFieldMatchData* matchData, bool strict) override;
    uint32_t get_committed_docid_limit() const noexcept override;
};

}
