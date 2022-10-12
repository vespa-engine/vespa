// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multinumericpostattribute.h"
#include "multi_numeric_enum_search_context.h"
#include <charconv>

namespace search {

template <typename B, typename M>
void
MultiValueNumericPostingAttribute<B, M>::freezeEnumDictionary()
{
    this->getEnumStore().freeze_dictionary();
}

template <typename B, typename M>
void
MultiValueNumericPostingAttribute<B, M>::mergeMemoryStats(vespalib::MemoryUsage & total)
{
    auto& compaction_strategy = this->getConfig().getCompactionStrategy();
    total.merge(this->getPostingList().update_stat(compaction_strategy));
}

template <typename B, typename M>
void
MultiValueNumericPostingAttribute<B, M>::applyValueChanges(const DocIndices& docIndices,
                                                           EnumStoreBatchUpdater& updater)
{
    using PostingChangeComputer = PostingChangeComputerT<WeightedIndex, PostingMap>;

    EnumIndexMapper mapper;
    PostingMap changePost(PostingChangeComputer::compute(this->getMultiValueMapping(), docIndices,
                                                         this->getEnumStore().get_comparator(), mapper));
    this->updatePostings(changePost);
    MultiValueNumericEnumAttribute<B, M>::applyValueChanges(docIndices, updater);
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
MultiValueNumericPostingAttribute<B, M>::reclaim_memory(generation_t oldest_used_gen)
{
    MultiValueNumericEnumAttribute<B, M>::reclaim_memory(oldest_used_gen);
    _postingList.reclaim_memory(oldest_used_gen);
}

template <typename B, typename M>
void
MultiValueNumericPostingAttribute<B, M>::before_inc_generation(generation_t current_gen)
{
    _postingList.freeze();
    MultiValueNumericEnumAttribute<B, M>::before_inc_generation(current_gen);
    _postingList.assign_generation(current_gen);
}

template <typename B, typename M>
std::unique_ptr<attribute::SearchContext>
MultiValueNumericPostingAttribute<B, M>::getSearch(QueryTermSimpleUP qTerm,
                                                   const attribute::SearchContextParams & params) const
{
    using BaseSC = attribute::MultiNumericEnumSearchContext<typename B::BaseClass::BaseType, M>;
    using SC = attribute::NumericPostingSearchContext<BaseSC, SelfType, int32_t>;
    auto doc_id_limit = this->getCommittedDocIdLimit();
    BaseSC base_sc(std::move(qTerm), *this, this->_mvMapping.make_read_view(doc_id_limit), this->_enumStore);
    return std::make_unique<SC>(std::move(base_sc), params, *this);
}

template <typename B, typename M>
vespalib::datastore::EntryRef
MultiValueNumericPostingAttribute<B, M>::DocumentWeightAttributeAdapter::get_dictionary_snapshot() const
{
    const IEnumStoreDictionary& dictionary = self._enumStore.get_dictionary();
    return dictionary.get_frozen_root();
}

template <typename B, typename M>
IDocumentWeightAttribute::LookupResult
MultiValueNumericPostingAttribute<B, M>::DocumentWeightAttributeAdapter::lookup(const LookupKey & key, vespalib::datastore::EntryRef dictionary_snapshot) const
{
    const IEnumStoreDictionary& dictionary = self._enumStore.get_dictionary();
    int64_t int_term;
    if ( !key.asInteger(int_term)) {
        return LookupResult();
    }
    auto comp = self._enumStore.make_comparator(int_term);
    auto find_result = dictionary.find_posting_list(comp, dictionary_snapshot);
    if (find_result.first.valid()) {
        auto pidx = find_result.second;
        if (pidx.valid()) {
            const PostingList &plist = self.getPostingList();
            auto minmax = plist.getAggregated(pidx);
            return LookupResult(pidx, plist.frozenSize(pidx), minmax.getMin(), minmax.getMax(), find_result.first);
        }
    }
    return LookupResult();
}

template <typename B, typename M>
void
MultiValueNumericPostingAttribute<B, M>::DocumentWeightAttributeAdapter::collect_folded(vespalib::datastore::EntryRef enum_idx, vespalib::datastore::EntryRef dictionary_snapshot, const std::function<void(vespalib::datastore::EntryRef)>& callback)const
{
    (void) dictionary_snapshot;
    callback(enum_idx);
}

template <typename B, typename M>
void
MultiValueNumericPostingAttribute<B, M>::DocumentWeightAttributeAdapter::create(vespalib::datastore::EntryRef idx, std::vector<DocumentWeightIterator> &dst) const
{
    assert(idx.valid());
    self.getPostingList().beginFrozen(idx, dst);
}

template <typename B, typename M>
DocumentWeightIterator
MultiValueNumericPostingAttribute<B, M>::DocumentWeightAttributeAdapter::create(vespalib::datastore::EntryRef idx) const
{
    assert(idx.valid());
    return self.getPostingList().beginFrozen(idx);
}

template <typename B, typename M>
std::unique_ptr<queryeval::SearchIterator>
MultiValueNumericPostingAttribute<B, M>::DocumentWeightAttributeAdapter::make_bitvector_iterator(vespalib::datastore::EntryRef idx, uint32_t doc_id_limit, fef::TermFieldMatchData &match_data, bool strict) const
{
    return self.getPostingList().make_bitvector_iterator(idx, doc_id_limit, match_data, strict);
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

