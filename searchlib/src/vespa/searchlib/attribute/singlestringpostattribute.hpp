// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "singlestringpostattribute.h"
#include "single_string_enum_search_context.h"
#include <vespa/searchcommon/attribute/config.h>
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
SingleValueStringPostingAttributeT<B>::SingleValueStringPostingAttributeT(const vespalib::string & name)
    : SingleValueStringPostingAttributeT<B>(name, AttributeVector::Config(AttributeVector::BasicType::STRING))
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
    auto& compaction_strategy = this->getConfig().getCompactionStrategy();
    total.merge(this->_postingList.update_stat(compaction_strategy));
}

template <typename B>
void
SingleValueStringPostingAttributeT<B>::applyUpdateValueChange(const Change & c,
                                                              EnumStore & enumStore,
                                                              std::map<DocId, EnumIndex> &currEnumIndices)
{
    EnumIndex newIdx;
    if (c.has_entry_ref()) {
        newIdx = EnumIndex(vespalib::datastore::EntryRef(c.get_entry_ref()));
    } else {
        enumStore.find_index(c._data.raw(), newIdx);
    }

    currEnumIndices[c._doc] = newIdx;
}

template <typename B>
void
SingleValueStringPostingAttributeT<B>::
makePostingChange(const vespalib::datastore::EntryComparator &cmpa,
                  IEnumStoreDictionary& dictionary,
                  const std::map<DocId, EnumIndex> &currEnumIndices,
                  PostingMap &changePost)
{
    for (const auto& elem : currEnumIndices) {
        uint32_t docId = elem.first;
        EnumIndex oldIdx = this->_enumIndices[docId].load_relaxed();
        EnumIndex newIdx = elem.second;

        // add new posting
        auto remapped_new_idx = dictionary.remap_index(newIdx);
        changePost[EnumPostingPair(remapped_new_idx, &cmpa)].add(docId, 1);

        // remove old posting
        if ( oldIdx.valid()) {
            auto remapped_old_idx = dictionary.remap_index(oldIdx);
            changePost[EnumPostingPair(remapped_old_idx, &cmpa)].remove(docId);
        }
    }
}

template <typename B>
void
SingleValueStringPostingAttributeT<B>::applyValueChanges(EnumStoreBatchUpdater& updater)
{
    EnumStore & enumStore = this->getEnumStore();
    IEnumStoreDictionary& dictionary = enumStore.get_dictionary();
    PostingMap changePost;

    // used to make sure several arithmetic operations on the same document in a single commit works
    std::map<DocId, EnumIndex> currEnumIndices;

    // This avoids searching for the defaultValue in the enum store for each CLEARDOC in the change vector.
    this->cache_change_data_entry_ref(this->_defaultValue);
    for (const auto& change : this->_changes.getInsertOrder()) {
        auto enumIter = currEnumIndices.find(change._doc);
        EnumIndex oldIdx;
        if (enumIter != currEnumIndices.end()) {
            oldIdx = enumIter->second;
        } else {
            oldIdx = this->_enumIndices[change._doc].load_relaxed();
        }
        if (change._type == ChangeBase::UPDATE) {
            applyUpdateValueChange(change, enumStore, currEnumIndices);
        } else if (change._type == ChangeBase::CLEARDOC) {
            this->_defaultValue._doc = change._doc;
            applyUpdateValueChange(this->_defaultValue, enumStore, currEnumIndices);
        }
    }
    // We must clear the cached entry ref as the defaultValue might be located in another data buffer on later invocations.
    this->_defaultValue.clear_entry_ref();

    makePostingChange(enumStore.get_folded_comparator(), dictionary, currEnumIndices, changePost);

    this->updatePostings(changePost);

    SingleValueStringAttributeT<B>::applyValueChanges(updater);
}

template <typename B>
void
SingleValueStringPostingAttributeT<B>::reclaim_memory(generation_t oldest_used_gen)
{
    SingleValueStringAttributeT<B>::reclaim_memory(oldest_used_gen);
    _postingList.reclaim_memory(oldest_used_gen);
}

template <typename B>
void
SingleValueStringPostingAttributeT<B>::before_inc_generation(generation_t current_gen)
{
    _postingList.freeze();
    SingleValueStringAttributeT<B>::before_inc_generation(current_gen);
    _postingList.assign_generation(current_gen);
}

template <typename B>
std::unique_ptr<attribute::SearchContext>
SingleValueStringPostingAttributeT<B>::getSearch(QueryTermSimpleUP qTerm,
                                                 const attribute::SearchContextParams & params) const
{
    using BaseSC = attribute::SingleStringEnumSearchContext;
    using SC = attribute::StringPostingSearchContext<BaseSC, SelfType, vespalib::btree::BTreeNoLeafData>;
    bool cased = this->get_match_is_cased();
    BaseSC base_sc(std::move(qTerm), cased, *this, &this->_enumIndices.acquire_elem_ref(0), this->_enumStore);
    return std::make_unique<SC>(std::move(base_sc),
                                params.useBitVector(),
                                *this);
}

} // namespace search

