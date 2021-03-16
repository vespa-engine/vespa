// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "singlestringpostattribute.h"
#include <vespa/searchlib/query/query_term_ucs4.h>

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
    this->getEnumStore().freeze_dictionary();
}

template <typename B>
void
SingleValueStringPostingAttributeT<B>::mergeMemoryStats(vespalib::MemoryUsage & total)
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
    enumStore.find_index(c._data.raw(), newIdx);

    currEnumIndices[c._doc] = newIdx;
}

template <typename B>
void
SingleValueStringPostingAttributeT<B>::
makePostingChange(const vespalib::datastore::EntryComparator *cmpa,
                  IEnumStoreDictionary& dictionary,
                  const std::map<DocId, EnumIndex> &currEnumIndices,
                  PostingMap &changePost)
{
    for (const auto& elem : currEnumIndices) {
        uint32_t docId = elem.first;
        EnumIndex oldIdx = this->_enumIndices[docId];
        EnumIndex newIdx = elem.second;

        // add new posting
        auto remapped_new_idx = dictionary.remap_index(newIdx);
        changePost[EnumPostingPair(remapped_new_idx, cmpa)].add(docId, 1);

        // remove old posting
        if ( oldIdx.valid()) {
            auto remapped_old_idx = dictionary.remap_index(oldIdx);
            changePost[EnumPostingPair(remapped_old_idx, cmpa)].remove(docId);
        }
    }
}

template <typename B>
void
SingleValueStringPostingAttributeT<B>::applyValueChanges(EnumStoreBatchUpdater& updater)
{
    EnumStore & enumStore = this->getEnumStore();
    IEnumStoreDictionary& dictionary = enumStore.get_dictionary();
    auto cmp = enumStore.make_folded_comparator();
    PostingMap changePost;

    // used to make sure several arithmetic operations on the same document in a single commit works
    std::map<DocId, EnumIndex> currEnumIndices;

    for (const auto& change : this->_changes) {
        auto enumIter = currEnumIndices.find(change._doc);
        EnumIndex oldIdx;
        if (enumIter != currEnumIndices.end()) {
            oldIdx = enumIter->second;
        } else {
            oldIdx = this->_enumIndices[change._doc];
        }
        if (change._type == ChangeBase::UPDATE) {
            applyUpdateValueChange(change, enumStore,
                                   currEnumIndices);
        } else if (change._type == ChangeBase::CLEARDOC) {
            this->_defaultValue._doc = change._doc;
            applyUpdateValueChange(this->_defaultValue, enumStore,
                                   currEnumIndices);
        }
    }

    makePostingChange(&cmp, dictionary, currEnumIndices, changePost);

    this->updatePostings(changePost);

    SingleValueStringAttributeT<B>::applyValueChanges(updater);
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
    return std::make_unique<StringSinglePostingSearchContext>(std::move(qTerm),
                                                              params.useBitVector(),
                                                              *this);
}

} // namespace search

