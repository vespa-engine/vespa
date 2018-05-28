// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "stringattribute.h"
#include "multistringattribute.h"
#include "enumattribute.hpp"
#include "multienumattribute.hpp"
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/searchlib/query/queryterm.h>

namespace search {

//-----------------------------------------------------------------------------
// MultiValueStringAttributeT public
//-----------------------------------------------------------------------------
template <typename B, typename M>
MultiValueStringAttributeT<B, M>::
MultiValueStringAttributeT(const vespalib::string &name,
                           const AttributeVector::Config &c)
    : MultiValueEnumAttribute<B, M>(name, c)
{ }

template <typename B, typename M>
MultiValueStringAttributeT<B, M>::~MultiValueStringAttributeT() { }


template <typename B, typename M>
void
MultiValueStringAttributeT<B, M>::freezeEnumDictionary()
{
    this->getEnumStore().freezeTree();
}


template <typename B, typename M>
AttributeVector::SearchContext::UP
MultiValueStringAttributeT<B, M>::getSearch(QueryTermSimpleUP qTerm,
                                            const attribute::SearchContextParams &) const
{
    if (this->getCollectionType() == attribute::CollectionType::WSET) {
        return std::make_unique<StringTemplSearchContext<StringSetImplSearchContext>>(std::move(qTerm), *this);
    } else {
        return std::make_unique<StringTemplSearchContext<StringArrayImplSearchContext>>(std::move(qTerm), *this);
    }
}

namespace {

template <typename E>
class EnumAccessor {
public:
    EnumAccessor(const E & enumStore) : _enumStore(enumStore) { }
    const char * get(typename E::Index index) const { return _enumStore.getValue(index); }
private:
    const E & _enumStore;
};

}

template <typename B, typename M>
int32_t
MultiValueStringAttributeT<B, M>::StringSetImplSearchContext::onFind(DocId doc, int32_t elemId, int32_t &weight) const
{
    StringAttribute::StringSearchContext::CollectWeight collector;
    return this->findNextWeight(doc, elemId, weight, collector);
}

template <typename B, typename M>
int32_t
MultiValueStringAttributeT<B, M>::StringArrayImplSearchContext::onFind(DocId doc, int32_t elemId, int32_t &weight) const
{
    StringAttribute::StringSearchContext::CollectHitCount collector;
    return this->findNextWeight(doc, elemId, weight, collector);
}

template <typename B, typename M>
template <typename Collector>
int32_t
MultiValueStringAttributeT<B, M>::StringImplSearchContext::findNextWeight(DocId doc, int32_t elemId, int32_t & weight, Collector & collector) const
{
    WeightedIndexArrayRef indices(myAttribute()._mvMapping.get(doc));

    EnumAccessor<typename B::EnumStore> accessor(myAttribute()._enumStore);
    int32_t foundElem = findNextMatch(indices, elemId, accessor, collector);
    weight = collector.getWeight();
    return foundElem;
}

template <typename B, typename M>
int32_t
MultiValueStringAttributeT<B, M>::StringImplSearchContext::onFind(DocId doc, int32_t elemId) const
{
    const MultiValueStringAttributeT<B, M> & attr(static_cast< const MultiValueStringAttributeT<B, M> & > (attribute()));
    WeightedIndexArrayRef indices(attr._mvMapping.get(doc));
    for (uint32_t i(elemId); i < indices.size(); i++) {
        if (isMatch(attr._enumStore.getValue(indices[i].value()))) {
            return i;
        }
    }

    return -1;
}

template <typename B, typename M>
template <typename BT>
MultiValueStringAttributeT<B, M>::StringTemplSearchContext<BT>::
StringTemplSearchContext(QueryTermSimpleUP qTerm, const AttrType & toBeSearched) :
    BT(std::move(qTerm), toBeSearched),
    EnumHintSearchContext(toBeSearched.getEnumStore().getEnumStoreDict(),
                          toBeSearched.getCommittedDocIdLimit(),
                          toBeSearched.getStatus().getNumValues())
{
    const EnumStore &enumStore(toBeSearched.getEnumStore());

    this->_plsc = static_cast<attribute::IPostingListSearchContext *>(this);
    if (this->valid()) {
        if (this->isPrefix()) {
            FoldedComparatorType comp(enumStore, queryTerm().getTerm(), true);
            lookupRange(comp, comp);
        } else if (this->isRegex()) {
            vespalib::string prefix(vespalib::Regexp::get_prefix(this->queryTerm().getTerm()));
            FoldedComparatorType comp(enumStore, prefix.c_str(), true);
            lookupRange(comp, comp);
        } else {
            FoldedComparatorType comp(enumStore, queryTerm().getTerm());
            lookupTerm(comp);
        }
    }
}

} // namespace search

