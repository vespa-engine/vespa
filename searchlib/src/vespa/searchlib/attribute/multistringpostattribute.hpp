// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "stringattribute.h"
#include "multistringpostattribute.h"
#include "multistringattribute.hpp"
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/query/queryterm.h>

namespace search {

template <typename B, typename T>
MultiValueStringPostingAttributeT<B, T>::MultiValueStringPostingAttributeT(const vespalib::string & name, const AttributeVector::Config & c)
    : MultiValueStringAttributeT<B, T>(name, c),
      PostingParent(*this, this->getEnumStore()),
      _document_weight_attribute_adapter(*this)
{
}

template <typename B, typename T>
MultiValueStringPostingAttributeT<B, T>::~MultiValueStringPostingAttributeT()
{
    this->disableFreeLists();
    this->disableElemHoldList();
    clearAllPostings();
}

class StringEnumIndexMapper : public EnumIndexMapper
{
public:
    StringEnumIndexMapper(const EnumPostingTree & dictionary) : _dictionary(dictionary) { }
    EnumStoreBase::Index map(EnumStoreBase::Index original, const EnumStoreComparator & compare) const override;
    virtual bool hasFold() const override { return true; }
private:
    const EnumPostingTree & _dictionary;
};

template <typename B, typename T>
void
MultiValueStringPostingAttributeT<B, T>::
applyValueChanges(const DocIndices &docIndices, EnumStoreBase::IndexVector &unused)
{
    typedef PostingChangeComputerT<WeightedIndex, PostingMap> PostingChangeComputer;
    EnumStore &enumStore(this->getEnumStore());
    Dictionary &dict(enumStore.getPostingDictionary());
    FoldedComparatorType compare(enumStore);

    StringEnumIndexMapper mapper(dict);
    PostingMap changePost(PostingChangeComputer::compute(this->getMultiValueMapping(), docIndices, compare, mapper));
    this->updatePostings(changePost);
    MultiValueStringAttributeT<B, T>::applyValueChanges(docIndices, unused);
}

template <typename B, typename T>
void
MultiValueStringPostingAttributeT<B, T>::freezeEnumDictionary()
{
    this->getEnumStore().freezeTree();
}

template <typename B, typename T>
void
MultiValueStringPostingAttributeT<B, T>::mergeMemoryStats(MemoryUsage &total)
{
    total.merge(this->_postingList.getMemoryUsage());
}


template <typename B, typename T>
void
MultiValueStringPostingAttributeT<B, T>::removeOldGenerations(generation_t firstUsed)
{
    MultiValueStringAttributeT<B, T>::removeOldGenerations(firstUsed);
    _postingList.trimHoldLists(firstUsed);
}

template <typename B, typename T>
void
MultiValueStringPostingAttributeT<B, T>::onGenerationChange(generation_t generation)
{
    _postingList.freeze();
    MultiValueStringAttributeT<B, T>::onGenerationChange(generation);
    _postingList.transferHoldLists(generation - 1);
}


template <typename B, typename T>
AttributeVector::SearchContext::UP
MultiValueStringPostingAttributeT<B, T>::getSearch(QueryTermSimpleUP qTerm,
                                                   const attribute::SearchContextParams & params) const
{
    std::unique_ptr<search::AttributeVector::SearchContext> sc;
    sc.reset(new typename std::conditional<T::_hasWeight,
                                           StringSetPostingSearchContext,
                                           StringArrayPostingSearchContext>::
             type(std::move(qTerm), params.useBitVector(), *this));
    return sc;
}


template <typename B, typename T>
IDocumentWeightAttribute::LookupResult
MultiValueStringPostingAttributeT<B, T>::DocumentWeightAttributeAdapter::lookup(const vespalib::string &term) const
{
    const Dictionary &dictionary = self._enumStore.getPostingDictionary();
    const FrozenDictionary frozenDictionary(dictionary.getFrozenView());
    DictionaryConstIterator dictItr(btree::BTreeNode::Ref(), dictionary.getAllocator());
    FoldedComparatorType comp(self._enumStore, term.c_str());

    dictItr.lower_bound(frozenDictionary.getRoot(), EnumIndex(), comp);
    if (dictItr.valid() && !comp(EnumIndex(), dictItr.getKey())) {
        datastore::EntryRef pidx = dictItr.getData();
        if (pidx.valid()) {
            const PostingList &plist = self.getPostingList();
            auto minmax = plist.getAggregated(pidx);
            return LookupResult(pidx, plist.frozenSize(pidx), minmax.getMin(), minmax.getMax());
        }
    }
    return LookupResult();
}

template <typename B, typename T>
void
MultiValueStringPostingAttributeT<B, T>::DocumentWeightAttributeAdapter::create(datastore::EntryRef idx, std::vector<DocumentWeightIterator> &dst) const
{
    assert(idx.valid());
    self.getPostingList().beginFrozen(idx, dst);
}

template <typename B, typename M>
DocumentWeightIterator
MultiValueStringPostingAttributeT<B, M>::DocumentWeightAttributeAdapter::create(datastore::EntryRef idx) const
{
    assert(idx.valid());
    return self.getPostingList().beginFrozen(idx);
}

template <typename B, typename T>
const IDocumentWeightAttribute *
MultiValueStringPostingAttributeT<B, T>::asDocumentWeightAttribute() const
{
    if (this->hasWeightedSetType() &&
        this->getBasicType() == AttributeVector::BasicType::STRING &&
        !this->getConfig().getIsFilter()) {
        return &_document_weight_attribute_adapter;
    }
    return nullptr;
}

} // namespace search

