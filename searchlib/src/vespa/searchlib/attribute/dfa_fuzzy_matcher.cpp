// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dfa_fuzzy_matcher.h"
#include "i_enum_store_dictionary.h"
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/text/lowercase.h>

using vespalib::fuzzy::LevenshteinDfa;
using vespalib::LowerCase;
using vespalib::Utf8Reader;
using vespalib::Utf8ReaderForZTS;

namespace search::attribute {

namespace {

std::vector<uint32_t>
extract_prefix(std::string_view target, uint32_t prefix_size, bool cased)
{
    std::vector<uint32_t> result;
    result.reserve(prefix_size);
    Utf8Reader reader(vespalib::stringref(target.data(), target.size()));
    for (size_t pos = 0; pos < prefix_size && reader.hasMore(); ++pos) {
        uint32_t code_point = reader.getChar();
        if (!cased) {
            code_point = LowerCase::convert(code_point);
        }
        result.emplace_back(code_point);
    }
    return result;
}

std::string_view
extract_suffix(std::string_view target, uint32_t prefix_size)
{
    Utf8Reader reader(vespalib::stringref(target.data(), target.size()));
    for (size_t pos = 0; pos < prefix_size && reader.hasMore(); ++pos) {
        (void) reader.getChar();
    }
    std::string_view result = target;
    result.remove_prefix(reader.getPos());
    return result;
}

}

DfaFuzzyMatcher::DfaFuzzyMatcher(std::string_view target, uint8_t max_edits, uint32_t prefix_size, bool cased, LevenshteinDfa::DfaType dfa_type)
    : _dfa(vespalib::fuzzy::LevenshteinDfa::build(extract_suffix(target, prefix_size), max_edits, (cased ? LevenshteinDfa::Casing::Cased : LevenshteinDfa::Casing::Uncased), dfa_type)),
      _successor(),
      _prefix(extract_prefix(target, prefix_size, cased)),
      _prefix_size(prefix_size),
      _cased(cased)
{
    _successor = _prefix;
}

DfaFuzzyMatcher::~DfaFuzzyMatcher() = default;

const char*
DfaFuzzyMatcher::skip_prefix(const char* word) const
{
    Utf8ReaderForZTS reader(word);
    size_t pos = 0;
    for (; pos < _prefix.size() && reader.hasMore(); ++pos) {
        (void) reader.getChar();
    }
    assert(pos == _prefix.size());
    return reader.get_current_ptr();
}

bool
DfaFuzzyMatcher::is_match(const char* word) const
{
    if (_prefix_size > 0) {
        Utf8ReaderForZTS reader(word);
        size_t pos = 0;
        for (; pos < _prefix.size() && reader.hasMore(); ++pos) {
            uint32_t code_point = reader.getChar();
            if (!_cased) {
                code_point = LowerCase::convert(code_point);
            }
            if (code_point != _prefix[pos]) {
                break;
            }
        }
        if (!reader.hasMore() && pos == _prefix.size() && pos < _prefix_size) {
            return true;
        }
        if (pos != _prefix_size) {
            return false;
        }
        word = reader.get_current_ptr();
    }
    auto match = _dfa.match(word);
    return match.matches();
}

template <typename DictionaryConstIteratorType>
bool
DfaFuzzyMatcher::is_match(const char* word, DictionaryConstIteratorType& itr, const DfaStringComparator::DataStoreType& data_store) {
    if (_prefix_size > 0) {
        word = skip_prefix(word);
        if (_prefix.size() < _prefix_size) {
            if (*word == '\0') {
                return true;
            }
            _successor.resize(_prefix.size());
            _successor.emplace_back(beyond_unicode);
        } else {
            _successor.resize(_prefix.size());
            auto match = _dfa.match(word, _successor);
            if (match.matches()) {
                return true;
            }
        }
    } else {
        _successor.clear();
        auto match = _dfa.match(word, _successor);
        if (match.matches()) {
            return true;
        }
    }
    DfaStringComparator cmp(data_store, _successor, _cased);
    assert(cmp.less(itr.getKey().load_acquire(), vespalib::datastore::EntryRef()));
    itr.seek(vespalib::datastore::AtomicEntryRef(), cmp);
    return false;
}

template
bool
DfaFuzzyMatcher::is_match(const char* word, EnumPostingTree::ConstIterator& itr, const DfaStringComparator::DataStoreType& data_store);

template
bool
DfaFuzzyMatcher::is_match(const char* word, EnumTree::ConstIterator& itr, const DfaStringComparator::DataStoreType& data_store);

}
