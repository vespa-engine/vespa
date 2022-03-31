// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_search_helper.h"
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/text/utf8.h>

namespace search::attribute {

StringSearchHelper::StringSearchHelper(QueryTermUCS4 & term, bool cased)
    : _regex(),
      _fuzzyMatcher(),
      _term(),
      _termLen(),
      _isPrefix(term.isPrefix()),
      _isRegex(term.isRegex()),
      _isCased(cased),
      _isFuzzy(term.isFuzzy())
{
    if (isRegex()) {
        if (isCased()) {
            _regex = vespalib::Regex::from_pattern(term.getTerm(), vespalib::Regex::Options::None);
        } else {
            _regex = vespalib::Regex::from_pattern(term.getTerm(), vespalib::Regex::Options::IgnoreCase);
        }
    } else if (isCased()) {
        _term._char = term.getTerm();
        _termLen = term.getTermLen();
    } else if (isFuzzy()) {
        _fuzzyMatcher = vespalib::FuzzyMatcher::from_term(term.getTerm());
    } else {
        term.term(_term._ucs4);
    }
}

StringSearchHelper::StringSearchHelper(StringSearchHelper&&) noexcept = default;

StringSearchHelper::~StringSearchHelper() = default;

bool
StringSearchHelper::isMatch(const char *src) const {
    if (__builtin_expect(isRegex(), false)) {
        return getRegex().valid() ? getRegex().partial_match(std::string_view(src)) : false;
    }
    if (__builtin_expect(isCased(), false)) {
        int res = strncmp(_term._char, src, _termLen);
        return (res == 0) && (src[_termLen] == 0 || isPrefix());
    }
    if (__builtin_expect(isFuzzy(), false)) {
        return getFuzzyMatcher().isMatch(src);
    }
    vespalib::Utf8ReaderForZTS u8reader(src);
    uint32_t j = 0;
    uint32_t val;
    for (;; ++j) {
        val = u8reader.getChar();
        val = vespalib::LowerCase::convert(val);
        if (_term._ucs4[j] == 0 || _term._ucs4[j] != val) {
            break;
        }
    }
    return (_term._ucs4[j] == 0 && (val == 0 || isPrefix()));
}

}
