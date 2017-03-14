// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/singlestringpostattribute.h>

namespace search {

template <typename B>
SingleValueStringPostingAttributeT<B>::SingleValueStringPostingAttributeT(const vespalib::string & name,
                                                                          const AttributeVector::Config & c) :
    SingleValueStringAttributeT<B>(name, c),
    PostingParent(*this, this->getEnumStore())
{
}

template <typename B>
SingleValueStringPostingAttributeT<B>::~SingleValueStringPostingAttributeT()
{
    this->disableFreeLists();
    this->disableElemHoldList();
    clearAllPostings();
}

template <typename B>
void
SingleValueStringPostingAttributeT<B>::freezeEnumDictionary()
{
    this->getEnumStore().freezeTree();
}

template <typename B>
void
SingleValueStringPostingAttributeT<B>::mergeMemoryStats(MemoryUsage & total)
{
    total.merge(this->_postingList.getMemoryUsage());
}

template <typename B>
void
SingleValueStringPostingAttributeT<B>::applyUpdateValueChange(const Change & c,
                                                              EnumStore & enumStore,
                                                              std::map<DocId, EnumIndex> &currEnumIndices)
{
    EnumIndex newIdx;
    enumStore.findIndex(c._data.raw(), newIdx);

    currEnumIndices[c._doc] = newIdx;

}


template <typename B>
void
SingleValueStringPostingAttributeT<B>::
makePostingChange(const EnumStoreComparator *cmpa,
                  Dictionary &dict,
                  const std::map<DocId, EnumIndex> &currEnumIndices,
                  PostingMap &changePost)
{
    typedef typename std::map<DocId, EnumIndex>::const_iterator EnumIter;
    for (EnumIter iter = currEnumIndices.begin(), end = currEnumIndices.end();
         iter != end; ++iter) {

        uint32_t docId = iter->first;
        EnumIndex oldIdx = this->_enumIndices[docId];
        EnumIndex newIdx = iter->second;

        // add new posting
        DictionaryIterator addItr = dict.find(newIdx, *cmpa);
        changePost[EnumPostingPair(addItr.getKey(), cmpa)].add(docId, 1);

        // remove old posting
        if ( oldIdx.valid()) {
            DictionaryIterator rmItr = dict.find(oldIdx, *cmpa);
            changePost[EnumPostingPair(rmItr.getKey(), cmpa)].remove(docId);
        }
    }
}


template <typename B>
void
SingleValueStringPostingAttributeT<B>::applyValueChanges(EnumStoreBase::IndexVector & unused)
{
    EnumStore & enumStore = this->getEnumStore();
    Dictionary & dict = enumStore.getPostingDictionary();
    FoldedComparatorType cmpa(enumStore);
    PostingMap changePost;

    // used to make sure several arithmetic operations on the same document in a single commit works
    std::map<DocId, EnumIndex> currEnumIndices;

    typedef ChangeVector::const_iterator CVIterator;
    for (CVIterator iter = this->_changes.begin(), end = this->_changes.end(); iter != end; ++iter) {
        typename std::map<DocId, EnumIndex>::const_iterator enumIter = currEnumIndices.find(iter->_doc);
        EnumIndex oldIdx;
        if (enumIter != currEnumIndices.end()) {
            oldIdx = enumIter->second;
        } else {
            oldIdx = this->_enumIndices[iter->_doc];
        }
        if (iter->_type == ChangeBase::UPDATE) {
            applyUpdateValueChange(*iter, enumStore,
                                   currEnumIndices);
        } else if (iter->_type == ChangeBase::CLEARDOC) {
            this->_defaultValue._doc = iter->_doc;
            applyUpdateValueChange(this->_defaultValue, enumStore,
                                   currEnumIndices);
        }
    }

    makePostingChange(&cmpa, dict, currEnumIndices, changePost);

    this->updatePostings(changePost);

    SingleValueStringAttributeT<B>::applyValueChanges(unused);
}

template <typename B>
void
SingleValueStringPostingAttributeT<B>::removeOldGenerations(generation_t firstUsed)
{
    SingleValueStringAttributeT<B>::removeOldGenerations(firstUsed);
    _postingList.trimHoldLists(firstUsed);
}

template <typename B>
void
SingleValueStringPostingAttributeT<B>::onGenerationChange(generation_t generation)
{
    _postingList.freeze();
    SingleValueStringAttributeT<B>::onGenerationChange(generation);
    _postingList.transferHoldLists(generation - 1);
}

template <typename B>
AttributeVector::SearchContext::UP
SingleValueStringPostingAttributeT<B>::getSearch(QueryTermSimpleUP qTerm,
                                                 const attribute::SearchContextParams & params) const
{
    return std::unique_ptr<search::AttributeVector::SearchContext>
        (new StringSinglePostingSearchContext(std::move(qTerm),
                                              params.useBitVector(),
                                              *this));
}


} // namespace search

