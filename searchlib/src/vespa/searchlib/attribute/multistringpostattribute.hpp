// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "stringattribute.h"
#include "multistringpostattribute.h"
#include "multistringattribute.hpp"
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/query/query_term_simple.h>

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
    IEnumStore::Index map(IEnumStore::Index original, const datastore::EntryComparator& compare) const override;
    virtual bool hasFold() const override { return true; }
private:
    const EnumPostingTree & _dictionary;
};

template <typename B, typename T>
void
MultiValueStringPostingAttributeT<B, T>::
applyValueChanges(const DocIndices& docIndices, EnumStoreBatchUpdater &updater)
{
    using PostingChangeComputer = PostingChangeComputerT<WeightedIndex, PostingMap>;
    EnumStore &enumStore(this->getEnumStore());
    Dictionary &dict(enumStore.get_posting_dictionary());
    auto compare = enumStore.make_folded_comparator();

    StringEnumIndexMapper mapper(dict);
    PostingMap changePost(PostingChangeComputer::compute(this->getMultiValueMapping(), docIndices, compare, mapper));
    this->updatePostings(changePost);
    MultiValueStringAttributeT<B, T>::applyValueChanges(docIndices, updater);
}

template <typename B, typename T>
void
MultiValueStringPostingAttributeT<B, T>::freezeEnumDictionary()
{
    this->getEnumStore().freeze_dictionary();
}

template <typename B, typename T>
void
MultiValueStringPostingAttributeT<B, T>::mergeMemoryStats(vespalib::MemoryUsage &total)
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
    const Dictionary &dictionary = self._enumStore.get_posting_dictionary();
    const FrozenDictionary frozenDictionary(dictionary.getFrozenView());
    DictionaryConstIterator dictItr(btree::BTreeNode::Ref(), dictionary.getAllocator());
    auto comp = self._enumStore.make_folded_comparator(term.c_str());

    dictItr.lower_bound(frozenDictionary.getRoot(), EnumIndex(), comp);
    if (dictItr.valid() && !comp(EnumIndex(), dictItr.getKey())) {
        datastore::EntryRef pidx(dictItr.getData());
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

