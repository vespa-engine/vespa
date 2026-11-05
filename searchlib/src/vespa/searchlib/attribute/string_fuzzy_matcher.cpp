// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_fuzzy_matcher.h"

#include "enumstore.h"
#include "i_dictionary_lookup.h"

#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>

namespace search::attribute {

StringFuzzyMatcher::StringFuzzyMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased,
                                       vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm)
    : _query_term(static_cast<QueryTermUCS4*>(query_term.release())),
      _helper(*_query_term, cased, fuzzy_matching_algorithm) {
}

StringFuzzyMatcher::StringFuzzyMatcher(StringFuzzyMatcher&&) noexcept = default;

StringFuzzyMatcher::~StringFuzzyMatcher() = default;

bool StringFuzzyMatcher::lookup_in_dictionary(const EnumStoreT<const char*>& enum_store,
                                              IDictionaryLookup&             dict) const {
    if (is_valid()) {
        std::string prefix(_helper.get_fuzzy_matcher().getPrefix());
        auto        comp = enum_store.prefix_lookup_comparator(prefix.c_str());
        dict.lookupRange(comp, comp);
        return true;
    }

    return false;
}

bool StringFuzzyMatcher::use_dictionary_entry(const EnumStoreT<const char*>&  enum_store,
                                              EnumPostingTree::ConstIterator& it) const {
    if (_helper.is_cased()) {
        if (match(enum_store.get_value(it.getKey().load_acquire()))) {
            return true;
        }
        ++it;
        return false;
    }

    return _helper.is_fuzzy_match(enum_store.get_value(it.getKey().load_acquire()), it, enum_store.get_data_store());
}

bool StringFuzzyMatcher::use_posting_lists_when_non_strict(const queryeval::ExecuteInfo& info,
                                                           uint32_t                      docid_limit) const {
    uint32_t           exp_doc_hits = docid_limit * info.hit_rate();
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
    return exp_doc_hits > fuzzy_use_posting_lists_doc_limit;
}

} // namespace search::attribute
