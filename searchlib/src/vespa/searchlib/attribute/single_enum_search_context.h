// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "search_context.h"
#include "enumstore.h"

namespace search::attribute {

/*
 * SingleEnumSearchContext handles the creation of search iterators for
 * a query term on a single value enumerated attribute vector.
 * This class should be considered to be an abstract class.
 */
template <typename T, typename BaseSC>
class SingleEnumSearchContext : public BaseSC
{
protected:
    using DocId = ISearchContext::DocId;
    const vespalib::datastore::AtomicEntryRef* _enum_indices;
    const EnumStoreT<T>&        _enum_store;

    int32_t onFind(DocId docId, int32_t elemId, int32_t & weight) const final {
        return find(docId, elemId, weight);
    }

    int32_t onFind(DocId docId, int32_t elemId) const final {
        return find(docId, elemId);
    }

public:
    SingleEnumSearchContext(typename BaseSC::MatcherType&& matcher, const AttributeVector& toBeSearched, const vespalib::datastore::AtomicEntryRef* enum_indices, const EnumStoreT<T>& enum_store);

    int32_t find(DocId docId, int32_t elemId, int32_t & weight) const {
        if ( elemId != 0) return -1;
        T v = _enum_store.get_value(_enum_indices[docId].load_acquire());
        weight = 1;
        return this->match(v) ? 0 : -1;
    }

    int32_t find(DocId docId, int32_t elemId) const {
        if ( elemId != 0) return -1;
        T v = _enum_store.get_value(_enum_indices[docId].load_acquire());
        return this->match(v) ? 0 : -1;
    }

    std::unique_ptr<queryeval::SearchIterator>
    createFilterIterator(fef::TermFieldMatchData* matchData, bool strict) override;
};

}
