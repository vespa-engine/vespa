// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multinumericpostattribute.h"
#include "multi_numeric_enum_search_context.h"
#include "numeric_direct_posting_store_adapter.hpp"
#include <vespa/searchcommon/attribute/config.h>
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
    total.merge(this->get_posting_store().update_stat(compaction_strategy));
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
      _posting_store_adapter(this->get_posting_store(), this->_enumStore, this->getIsFilter())
{
}

template <typename B, typename M>
MultiValueNumericPostingAttribute<B, M>::~MultiValueNumericPostingAttribute()
{
    this->disableFreeLists();
    this->disable_entry_hold_list();
    clearAllPostings();
}

template <typename B, typename M>
void
MultiValueNumericPostingAttribute<B, M>::reclaim_memory(generation_t oldest_used_gen)
{
    MultiValueNumericEnumAttribute<B, M>::reclaim_memory(oldest_used_gen);
    _posting_store.reclaim_memory(oldest_used_gen);
}

template <typename B, typename M>
void
MultiValueNumericPostingAttribute<B, M>::before_inc_generation(generation_t current_gen)
{
    _posting_store.freeze();
    MultiValueNumericEnumAttribute<B, M>::before_inc_generation(current_gen);
    _posting_store.assign_generation(current_gen);
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
const IDocidWithWeightPostingStore*
MultiValueNumericPostingAttribute<B, M>::as_docid_with_weight_posting_store() const
{
    if (this->hasWeightedSetType() && (this->getBasicType() == AttributeVector::BasicType::INT64)) {
        return &_posting_store_adapter;
    }
    return nullptr;
}

}

