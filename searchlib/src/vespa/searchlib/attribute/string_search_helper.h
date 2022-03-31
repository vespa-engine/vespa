// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastlib/text/unicodeutil.h>
#include <vespa/vespalib/regex/regex.h>
#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>

namespace search { class QueryTermUCS4; }

namespace search::attribute {

/**
 * Helper class for search context when scanning string fields
 * It handles different search settings like prefix, regex and cased/uncased.
 */
class StringSearchHelper {
public:
    StringSearchHelper(QueryTermUCS4 & qTerm, bool cased);
    StringSearchHelper(StringSearchHelper&&) noexcept;
    ~StringSearchHelper();
    bool isMatch(const char *src) const;
    bool isPrefix() const { return _isPrefix; }
    bool isRegex() const { return _isRegex; }
    bool isCased() const { return _isCased; }
    bool isFuzzy() const { return _isFuzzy; }
    const vespalib::Regex & getRegex() const { return _regex; }
    const vespalib::FuzzyMatcher & getFuzzyMatcher() const { return _fuzzyMatcher; }
private:
    vespalib::Regex                _regex;
    vespalib::FuzzyMatcher         _fuzzyMatcher;
    union {
        const ucs4_t *_ucs4;
        const char   *_char;
    }                              _term;
    uint32_t                       _termLen;
    bool                           _isPrefix;
    bool                           _isRegex;
    bool                           _isCased;
    bool                           _isFuzzy;
};

}
