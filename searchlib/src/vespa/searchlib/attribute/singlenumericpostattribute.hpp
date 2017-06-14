// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/singlenumericpostattribute.h>
#include <vespa/searchlib/attribute/enumstore.h>
#include <vespa/searchlib/attribute/enumcomparator.h>
#include <vespa/searchlib/attribute/singlenumericenumattribute.hpp>

namespace search {

template <typename B>
SingleValueNumericPostingAttribute<B>::~SingleValueNumericPostingAttribute()
{
    this->disableFreeLists();
    this->disableElemHoldList();
    clearAllPostings();
}

template <typename B>
SingleValueNumericPostingAttribute<B>::SingleValueNumericPostingAttribute(const vespalib::string & name,
                                                                          const AttributeVector::Config & c) :
    SingleValueNumericEnumAttribute<B>(name, c),
    PostingParent(*this, this->getEnumStore())
{
}

template <typename B>
void
SingleValueNumericPostingAttribute<B>::freezeEnumDictionary()
{
    this->getEnumStore().freezeTree();
}

template <typename B>
void
SingleValueNumericPostingAttribute<B>::mergeMemoryStats(MemoryUsage & total)
{
    total.merge(this->_postingList.getMemoryUsage());
}

template <typename B>
void
SingleValueNumericPostingAttribute<B>::applyUpdateValueChange(const Change & c,
                                                              EnumStore & enumStore,
                                                              std::map<DocId, EnumIndex> & currEnumIndices)
{
    EnumIndex newIdx;
    enumStore.findIndex(c._data.raw(), newIdx);
    currEnumIndices[c._doc] = newIdx;
}

template <typename B>
void
SingleValueNumericPostingAttribute<B>::
makePostingChange(const EnumStoreComparator *cmpa,
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
        changePost[EnumPostingPair(newIdx, cmpa)].add(docId, 1);

        // remove old posting
        if ( oldIdx.valid()) {
            changePost[EnumPostingPair(oldIdx, cmpa)].remove(docId);
        }
    }
}


template <typename B>
void
SingleValueNumericPostingAttribute<B>::applyValueChanges(EnumStoreBase::IndexVector & unused)
{
    EnumStore & enumStore = this->getEnumStore();
    Dictionary & dict = enumStore.getPostingDictionary();
    ComparatorType cmpa(enumStore);
    PostingMap changePost;

    // used to make sure several arithmetic operations on the same document in a single commit works
    std::map<DocId, EnumIndex> currEnumIndices;

    for (ChangeVectorIterator iter = this->_changes.begin(), end = this->_changes.end(); iter != end; ++iter) {
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
        } else if (iter->_type >= ChangeBase::ADD && iter->_type <= ChangeBase::DIV) {
            if (oldIdx.valid()) {
                T oldValue = enumStore.getValue(oldIdx);
                T newValue = this->applyArithmetic(oldValue, *iter);

                DictionaryIterator addItr = dict.find(EnumIndex(), ComparatorType(enumStore, newValue));
                EnumIndex newIdx = addItr.getKey();
                currEnumIndices[iter->_doc] = newIdx;
            }
        } else if(iter->_type == ChangeBase::CLEARDOC) {
            this->_defaultValue._doc = iter->_doc;
            applyUpdateValueChange(this->_defaultValue, enumStore,
                                   currEnumIndices);
        }
    }

    makePostingChange(&cmpa, currEnumIndices, changePost);

    this->updatePostings(changePost);
    SingleValueNumericEnumAttribute<B>::applyValueChanges(unused);
}

template <typename B>
void
SingleValueNumericPostingAttribute<B>::removeOldGenerations(generation_t firstUsed)
{
    SingleValueNumericEnumAttribute<B>::removeOldGenerations(firstUsed);
    _postingList.trimHoldLists(firstUsed);
}

template <typename B>
void
SingleValueNumericPostingAttribute<B>::onGenerationChange(generation_t generation)
{
    _postingList.freeze();
    SingleValueNumericEnumAttribute<B>::onGenerationChange(generation);
    _postingList.transferHoldLists(generation - 1);
}

template <typename B>
AttributeVector::SearchContext::UP
SingleValueNumericPostingAttribute<B>::getSearch(QueryTermSimple::UP qTerm,
                                                 const attribute::SearchContextParams & params) const
{
    return std::unique_ptr<AttributeVector::SearchContext>
        (new SinglePostingSearchContext(std::move(qTerm),
                                        params,
                                        *this));
}

} // namespace search

