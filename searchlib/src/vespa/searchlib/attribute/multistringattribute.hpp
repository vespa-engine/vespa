// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/stringattribute.h>
#include <vespa/searchlib/attribute/multistringattribute.h>
#include <vespa/searchlib/attribute/enumattribute.hpp>
#include <vespa/searchlib/attribute/multienumattribute.hpp>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/text/lowercase.h>
#include <set>
#include <string>

namespace search {

//-----------------------------------------------------------------------------
// MultiValueStringAttributeT public
//-----------------------------------------------------------------------------
template <typename B, typename M>
MultiValueStringAttributeT<B, M>::
MultiValueStringAttributeT(const vespalib::string &name,
                           const AttributeVector::Config &c)
    : MultiValueEnumAttribute<B, M>(name, c)
{
}

template <typename B, typename M>
MultiValueStringAttributeT<B, M>::~MultiValueStringAttributeT()
{
}


template <typename B, typename M>
void
MultiValueStringAttributeT<B, M>::freezeEnumDictionary(void)
{
    this->getEnumStore().freezeTree();
}


template <typename B, typename M>
AttributeVector::SearchContext::UP
MultiValueStringAttributeT<B, M>::getSearch(QueryTermSimple::UP qTerm,
                                            const AttributeVector::SearchContext::Params & params) const
{
    (void) params;
    std::unique_ptr<search::AttributeVector::SearchContext> sc;
    if (this->getCollectionType() == attribute::CollectionType::WSET) {
        sc.reset(new StringTemplSearchContext<StringSetImplSearchContext>(std::move(qTerm), *this));
    } else {
        sc.reset(new StringTemplSearchContext<StringArrayImplSearchContext>(std::move(qTerm), *this));
    }
    return sc;
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
bool
MultiValueStringAttributeT<B, M>::StringSetImplSearchContext::onCmp(DocId doc, int32_t & weight) const
{
    StringAttribute::StringSearchContext::CollectWeight collector;
    return this->collectWeight(doc, weight, collector);
}

template <typename B, typename M>
bool
MultiValueStringAttributeT<B, M>::StringArrayImplSearchContext::onCmp(DocId doc, int32_t & weight) const
{
    StringAttribute::StringSearchContext::CollectHitCount collector;
    return this->collectWeight(doc, weight, collector);
}

template <typename B, typename M>
template <typename Collector>
bool
MultiValueStringAttributeT<B, M>::StringImplSearchContext::collectWeight(DocId doc, int32_t & weight, Collector & collector) const
{
    WeightedIndexArrayRef indices(myAttribute()._mvMapping.get(doc));

    EnumAccessor<typename B::EnumStore> accessor(myAttribute()._enumStore);
    collectMatches(indices, accessor, collector);
    weight = collector.getWeight();
    return collector.hasMatch();
}

template <typename B, typename M>
bool
MultiValueStringAttributeT<B, M>::StringImplSearchContext::onCmp(DocId doc) const
{
    const MultiValueStringAttributeT<B, M> & attr(static_cast< const MultiValueStringAttributeT<B, M> & > (attribute()));
    WeightedIndexArrayRef indices(attr._mvMapping.get(doc));
    for (const WeightedIndex &wi : indices) {
        if (isMatch(attr._enumStore.getValue(wi.value()))) {
            return true;
        }
    }

    return false;
}

template <typename B, typename M>
template <typename BT>
MultiValueStringAttributeT<B, M>::StringTemplSearchContext<BT>::
StringTemplSearchContext(QueryTermSimple::UP qTerm, const AttrType & toBeSearched) :
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

