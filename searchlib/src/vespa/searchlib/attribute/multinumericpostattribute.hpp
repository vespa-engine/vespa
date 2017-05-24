// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multinumericpostattribute.h"

namespace search {

template <typename B, typename M>
void
MultiValueNumericPostingAttribute<B, M>::freezeEnumDictionary()
{
    this->getEnumStore().freezeTree();
}

template <typename B, typename M>
void
MultiValueNumericPostingAttribute<B, M>::mergeMemoryStats(MemoryUsage & total)
{
    total.merge(this->getPostingList().getMemoryUsage());
}

template <typename B, typename M>
void
MultiValueNumericPostingAttribute<B, M>::applyValueChanges(const DocIndices & docIndices, EnumStoreBase::IndexVector & unused)
{
    typedef PostingChangeComputerT<WeightedIndex, PostingMap> PostingChangeComputer;
    EnumStore & enumStore = this->getEnumStore();
    ComparatorType compare(enumStore);

    EnumIndexMapper mapper;
    PostingMap changePost(PostingChangeComputer::compute(this->getMultiValueMapping(), docIndices, compare, mapper));
    this->updatePostings(changePost);
    MultiValueNumericEnumAttribute<B, M>::applyValueChanges(docIndices, unused);
}


template <typename B, typename M>
MultiValueNumericPostingAttribute<B, M>::MultiValueNumericPostingAttribute(const vespalib::string & name,
                                                                           const AttributeVector::Config & cfg)
    : MultiValueNumericEnumAttribute<B, M>(name, cfg),
      PostingParent(*this, this->getEnumStore()),
      _document_weight_attribute_adapter(*this)
{
}

template <typename B, typename M>
MultiValueNumericPostingAttribute<B, M>::~MultiValueNumericPostingAttribute()
{
    this->disableFreeLists();
    this->disableElemHoldList();
    clearAllPostings();
}


template <typename B, typename M>
void
MultiValueNumericPostingAttribute<B, M>::removeOldGenerations(generation_t firstUsed)
{
    MultiValueNumericEnumAttribute<B, M>::removeOldGenerations(firstUsed);
    _postingList.trimHoldLists(firstUsed);
}

template <typename B, typename M>
void
MultiValueNumericPostingAttribute<B, M>::onGenerationChange(generation_t generation)
{
    _postingList.freeze();
    MultiValueNumericEnumAttribute<B, M>::onGenerationChange(generation);
    _postingList.transferHoldLists(generation - 1);
}

template <typename B, typename M>
AttributeVector::SearchContext::UP
MultiValueNumericPostingAttribute<B, M>::getSearch(QueryTermSimpleUP qTerm,
                                                   const attribute::SearchContextParams & params) const
{
    std::unique_ptr<search::AttributeVector::SearchContext> sc;
    sc.reset(new typename std::conditional<M::_hasWeight,
                                           SetPostingSearchContext,
                                           ArrayPostingSearchContext>::
             type(std::move(qTerm), params, *this));
    return sc;
}


template <typename B, typename M>
IDocumentWeightAttribute::LookupResult
MultiValueNumericPostingAttribute<B, M>::DocumentWeightAttributeAdapter::lookup(const vespalib::string &term) const
{
    const Dictionary &dictionary = self._enumStore.getPostingDictionary();
    const FrozenDictionary frozenDictionary(dictionary.getFrozenView());
    DictionaryConstIterator dictItr(btree::BTreeNode::Ref(), dictionary.getAllocator());

    char *end = nullptr;
    int64_t int_term = strtoll(term.c_str(), &end, 10);
    if (*end == '\0') {
        ComparatorType comp(self._enumStore, int_term);

        dictItr.lower_bound(frozenDictionary.getRoot(), EnumIndex(), comp);
        if (dictItr.valid() && !comp(EnumIndex(), dictItr.getKey())) {
            datastore::EntryRef pidx = dictItr.getData();
            if (pidx.valid()) {
                const PostingList &plist = self.getPostingList();
                auto minmax = plist.getAggregated(pidx);
                return LookupResult(pidx, plist.frozenSize(pidx), minmax.getMin(), minmax.getMax());
            }
        }
    }
    return LookupResult();
}

template <typename B, typename M>
void
MultiValueNumericPostingAttribute<B, M>::DocumentWeightAttributeAdapter::create(datastore::EntryRef idx, std::vector<DocumentWeightIterator> &dst) const
{
    assert(idx.valid());
    self.getPostingList().beginFrozen(idx, dst);
}

template <typename B, typename M>
DocumentWeightIterator
MultiValueNumericPostingAttribute<B, M>::DocumentWeightAttributeAdapter::create(datastore::EntryRef idx) const
{
    assert(idx.valid());
    return self.getPostingList().beginFrozen(idx);
}

template <typename B, typename M>
const IDocumentWeightAttribute *
MultiValueNumericPostingAttribute<B, M>::asDocumentWeightAttribute() const
{
    if (this->hasWeightedSetType() &&
        this->getBasicType() == AttributeVector::BasicType::INT64 &&
        !this->getConfig().getIsFilter()) {
        return &_document_weight_attribute_adapter;
    }
    return nullptr;
}

} // namespace search

