// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_string_enum_search_context.h"
#include "multistringpostattribute.h"
#include "string_direct_posting_store_adapter.hpp"
#include "string_matcher_factory.h"
#include "string_posting_search_context.hpp"
#include "string_range_posting_search_context.hpp"

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/query/query_term_simple.h>

#include <type_traits>

namespace search {

template <typename B, typename T>
MultiValueStringPostingAttributeT<B, T>::MultiValueStringPostingAttributeT(const std::string&             name,
                                                                           const AttributeVector::Config& c)
    : MultiValueStringAttributeT<B, T>(name, c),
      PostingParent(*this, this->getEnumStore()),
      _posting_store_adapter(this->get_posting_store(), this->_enumStore, this->getIsFilter()) {
}

template <typename B, typename T>
MultiValueStringPostingAttributeT<B, T>::MultiValueStringPostingAttributeT(const std::string& name)
    : MultiValueStringPostingAttributeT<B, T>(
          name, AttributeVector::Config(AttributeVector::BasicType::STRING, attribute::CollectionType::ARRAY)) {
}

template <typename B, typename T> MultiValueStringPostingAttributeT<B, T>::~MultiValueStringPostingAttributeT() {
    this->disableFreeLists();
    this->disable_entry_hold_list();
    clearAllPostings();
}

class StringEnumIndexMapper : public EnumIndexMapper {
public:
    StringEnumIndexMapper(IEnumStoreDictionary& dictionary) : _dictionary(dictionary) {}
    IEnumStore::Index map(IEnumStore::Index original) const override;
    bool hasFold() const override { return true; }

private:
    IEnumStoreDictionary& _dictionary;
};

template <typename B, typename T>
void MultiValueStringPostingAttributeT<B, T>::applyValueChanges(const DocIndices&      docIndices,
                                                                EnumStoreBatchUpdater& updater) {
    using PostingChangeComputer = PostingChangeComputerT<WeightedIndex, PostingMap>;
    EnumStore&            enumStore(this->getEnumStore());
    IEnumStoreDictionary& dictionary(enumStore.get_dictionary());

    StringEnumIndexMapper mapper(dictionary);
    PostingMap            changePost(PostingChangeComputer::compute(this->getMultiValueMapping(), docIndices,
                                                                    enumStore.get_lookup_source(), mapper));
    this->updatePostings(changePost);
    MultiValueStringAttributeT<B, T>::applyValueChanges(docIndices, updater);
}

template <typename B, typename T> void MultiValueStringPostingAttributeT<B, T>::freezeEnumDictionary() {
    this->getEnumStore().freeze_dictionary();
}

template <typename B, typename T>
void MultiValueStringPostingAttributeT<B, T>::mergeMemoryStats(vespalib::MemoryUsage& total) {
    auto& compaction_strategy = this->getConfig().getCompactionStrategy();
    total.merge(this->_posting_store.update_stat(compaction_strategy));
}

template <typename B, typename T>
void MultiValueStringPostingAttributeT<B, T>::reclaim_memory(vespalib::Generation oldest_used_gen) {
    MultiValueStringAttributeT<B, T>::reclaim_memory(oldest_used_gen);
    _posting_store.reclaim_memory(oldest_used_gen);
}

template <typename B, typename T>
void MultiValueStringPostingAttributeT<B, T>::before_inc_generation(vespalib::Generation current_gen) {
    _posting_store.freeze();
    MultiValueStringAttributeT<B, T>::before_inc_generation(current_gen);
    _posting_store.assign_generation(current_gen);
}

template <typename B, typename T>
std::unique_ptr<attribute::SearchContext>
MultiValueStringPostingAttributeT<B, T>::getSearch(QueryTermSimpleUP                     qTerm,
                                                   const attribute::SearchContextParams& params) const {
    bool cased = this->get_match_is_cased();
    auto doc_id_limit = this->getCommittedDocIdLimit();
    return attribute::StringMatcherFactory::create_and_apply(
        std::move(qTerm), cased, params.fuzzy_matching_algorithm(),
        [&]<typename Matcher>(Matcher&& matcher) -> std::unique_ptr<attribute::SearchContext> {
            using BaseSC = attribute::MultiStringEnumSearchContextT<T, Matcher>;
            using SC = std::conditional_t<std::is_same_v<Matcher, attribute::StringRangeMatcher>,
                                          attribute::StringRangePostingSearchContext<BaseSC, SelfType, int32_t>,
                                          attribute::StringPostingSearchContext<BaseSC, SelfType, int32_t>>;
            BaseSC base_sc(std::move(matcher), *this, this->_mvMapping.make_read_view(doc_id_limit),
                           this->_enumStore);
            return std::make_unique<SC>(std::move(base_sc), params.useBitVector(), *this);
        });
}

template <typename B, typename T>
const IDocidWithWeightPostingStore*
MultiValueStringPostingAttributeT<B, T>::as_docid_with_weight_posting_store() const {
    if (this->isStringType()) {
        return &_posting_store_adapter;
    }
    return nullptr;
}

} // namespace search
