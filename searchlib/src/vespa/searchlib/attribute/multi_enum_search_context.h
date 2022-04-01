// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "search_context.h"
#include "enumstore.h"
#include "multi_value_mapping.h"

namespace search::attribute {

/*
 * MultiEnumSearchContext handles the creation of search iterators for
 * a query term on a multi value enumerated attribute vector.
 * This class should be considered to be an abstract class.
 */
template <typename T, typename Matcher, typename M>
class MultiEnumSearchContext : public Matcher, public SearchContext
{
protected:
    const MultiValueMapping<M>& _mv_mapping;
    const EnumStoreT<T>&        _enum_store;

    int32_t onFind(DocId docId, int32_t elemId, int32_t & weight) const override {
        return find(docId, elemId, weight);
    }

    int32_t onFind(DocId docId, int32_t elemId) const override {
        return find(docId, elemId);
    }

    bool valid() const override { return this->isValid(); }

    MultiEnumSearchContext(Matcher&& matcher, const AttributeVector& toBeSearched, const MultiValueMapping<M>& mv_mapping, const EnumStoreT<T>& enum_store);

public:
    int32_t find(DocId doc, int32_t elemId, int32_t & weight) const {
        auto indices(_mv_mapping.get(doc));
        for (uint32_t i(elemId); i < indices.size(); i++) {
            T v = _enum_store.get_value(indices[i].value_ref().load_acquire());
            if (this->match(v)) {
                weight = indices[i].weight();
                return i;
            }
        }
        weight = 0;
        return -1;
    }

    int32_t find(DocId doc, int32_t elemId) const {
        auto indices(_mv_mapping.get(doc));
        for (uint32_t i(elemId); i < indices.size(); i++) {
            T v = _enum_store.get_value(indices[i].value_ref().load_acquire());
            if (this->match(v)) {
                return i;
            }
        }
        return -1;
    }
};

}
