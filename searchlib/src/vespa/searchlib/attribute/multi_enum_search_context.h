// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "numeric_search_context.h"
#include "enumstore.h"
#include "multi_value_mapping_read_view.h"
#include <vespa/searchcommon/attribute/multivalue.h>

namespace search::attribute {

/*
 * MultiEnumSearchContext handles the creation of search iterators for
 * a query term on a multi value enumerated attribute vector.
 * This class should be considered to be an abstract class.
 */
template <typename T, typename BaseSC, typename M>
class MultiEnumSearchContext : public BaseSC
{
protected:
    using DocId = ISearchContext::DocId;
    MultiValueMappingReadView<M> _mv_mapping_read_view;
    const EnumStoreT<T>&         _enum_store;

    int32_t onFind(DocId docId, int32_t elemId, int32_t & weight) const override {
        return find(docId, elemId, weight);
    }

    int32_t onFind(DocId docId, int32_t elemId) const override {
        return find(docId, elemId);
    }

    MultiEnumSearchContext(typename BaseSC::MatcherType&& matcher, const AttributeVector& toBeSearched, MultiValueMappingReadView<M> mv_mapping_read_view, const EnumStoreT<T>& enum_store);

public:
    int32_t find(DocId doc, int32_t elemId, int32_t & weight) const {
        auto indices(_mv_mapping_read_view.get(doc));
        for (uint32_t i(elemId); i < indices.size(); i++) {
            T v = _enum_store.get_value(multivalue::get_value_ref(indices[i]).load_acquire());
            if (this->match(v)) {
                weight = multivalue::get_weight(indices[i]);
                return i;
            }
        }
        weight = 0;
        return -1;
    }

    int32_t find(DocId doc, int32_t elemId) const {
        auto indices(_mv_mapping_read_view.get(doc));
        for (uint32_t i(elemId); i < indices.size(); i++) {
            T v = _enum_store.get_value(multivalue::get_value_ref(indices[i]).load_acquire());
            if (this->match(v)) {
                return i;
            }
        }
        return -1;
    }

    std::unique_ptr<queryeval::SearchIterator>
    createFilterIterator(fef::TermFieldMatchData* matchData, bool strict) override;
};

}
