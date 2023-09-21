// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dfa_string_comparator.h"
#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/fuzzy/levenshtein_dfa.h>

namespace search::attribute {

/**
 * Class that uses a LevenshteinDfa to fuzzy match a target word against words in a dictionary.
 *
 * The dictionary iterator is advanced based on the successor string from the DFA
 * each time the candidate word is _not_ a match.
 */
class DfaFuzzyMatcher {
private:
    vespalib::fuzzy::LevenshteinDfa _dfa;
    std::vector<uint32_t> _successor;

public:
    DfaFuzzyMatcher(std::string_view target, uint8_t max_edits, bool cased, vespalib::fuzzy::LevenshteinDfa::DfaType dfa_type);
    ~DfaFuzzyMatcher();

    template <typename DictionaryConstIteratorType>
    bool is_match(const char* word, DictionaryConstIteratorType& itr, const DfaStringComparator::DataStoreType& data_store) {
        auto match = _dfa.match(word, _successor);
        if (match.matches()) {
            return true;
        } else {
            DfaStringComparator cmp(data_store, _successor);
            itr.seek(vespalib::datastore::AtomicEntryRef(), cmp);
            return false;
        }
    }
};

}
