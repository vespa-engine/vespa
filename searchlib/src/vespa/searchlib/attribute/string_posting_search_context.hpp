// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "posting_list_folded_search_context.hpp"
#include "string_posting_search_context.h"

namespace search::attribute {

template <typename BaseSC, typename AttrT, typename DataT>
StringPostingSearchContext<BaseSC, AttrT, DataT>::StringPostingSearchContext(BaseSC&& base_sc, bool useBitVector,
                                                                             const AttrT& toBeSearched)
    : Parent(std::move(base_sc), useBitVector, toBeSearched) {
    if (this->valid()) {
        if (this->isPrefix()) {
            auto comp = _enumStore.make_folded_comparator_prefix(this->queryTerm()->getTerm());
            this->lookupRange(comp, comp);
        } else if (this->isRegex()) {
            std::string prefix(RegexpUtil::get_prefix(this->queryTerm()->getTerm()));
            auto        comp = _enumStore.make_folded_comparator_prefix(prefix.c_str());
            this->lookupRange(comp, comp);
        } else if (this->isFuzzy()) {
            std::string prefix(this->getFuzzyMatcher().getPrefix());
            auto        comp = _enumStore.make_folded_comparator_prefix(prefix.c_str());
            this->lookupRange(comp, comp);
        } else {
            auto comp = _enumStore.make_folded_comparator(this->queryTerm()->getTerm());
            this->lookupTerm(comp);
        }
        if (this->_uniqueValues == 1u) {
            /*
             * A single dictionary entry from lookupRange() might not be
             * a match if this is a regex search or a fuzzy search.
             */
            if (!this->_lowerDictItr.valid() || use_single_dictionary_entry(this->_lowerDictItr)) {
                this->lookupSingle();
            } else {
                this->_uniqueValues = 0;
            }
        }
    }
}

template <typename BaseSC, typename AttrT, typename DataT>
bool StringPostingSearchContext<BaseSC, AttrT, DataT>::use_dictionary_entry(
    PostingListSearchContext::DictionaryConstIterator& it) const {
    if (this->isRegex()) {
        if (this->getRegex().valid() &&
            this->getRegex().partial_match(_enumStore.get_value(it.getKey().load_acquire())))
        {
            return true;
        }
        ++it;
        return false;
    } else if (this->isCased()) {
        if (this->match(_enumStore.get_value(it.getKey().load_acquire()))) {
            return true;
        }
        ++it;
        return false;
    } else if (this->isFuzzy()) {
        return this->is_fuzzy_match(_enumStore.get_value(it.getKey().load_acquire()), it,
                                    _enumStore.get_data_store());
    }
    return true;
}

template <typename BaseSC, typename AttrT, typename DataT>
bool StringPostingSearchContext<BaseSC, AttrT, DataT>::use_posting_lists_when_non_strict(
    const ExecuteInfo& info) const {
    if (this->isFuzzy()) {
        uint32_t           exp_doc_hits = this->_docIdLimit * info.hit_rate();
        constexpr uint32_t fuzzy_use_posting_lists_doc_limit = 10000;
        /**
         * The above constant was derived after a query latency experiment with fuzzy matching
         * on 2M documents with a dictionary size of 292070.
         *
         * Cost per document in dfa-based fuzzy matching (scanning the dictionary and merging posting lists) - strict
         * iterator: 2.8 ms / 2k = 0.0014 ms 4.4 ms / 20k = 0.00022 ms 9.0 ms / 200k = 0.000045 ms 98 ms / 1M =
         * 0.000098 ms
         *
         * Cost per document in lookup-based fuzzy matching - non-strict iterator:
         *   7.6 ms / 2k = 0.0038 ms
         *   54 ms / 20k = 0.0027 ms
         *   529 ms / 200k = 0.0026 ms
         *
         * Based on this experiment, we observe that we should avoid lookup-based fuzzy matching
         * when the number of documents to calculate this on exceeds a number between 2000 - 20000.
         *
         * Also note that the cost of scanning the dictionary and performing the fuzzy matching
         * is already performed at this point.
         * The only work remaining if returning true is merging the posting lists.
         */
        if (exp_doc_hits > fuzzy_use_posting_lists_doc_limit) {
            return true;
        }
    }
    return false;
}

} // namespace search::attribute
