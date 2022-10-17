// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastlib/text/unicodeutil.h>
#include <vespa/vespalib/regex/regex.h>

namespace vespalib { class FuzzyMatcher; }
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
    StringSearchHelper(const StringSearchHelper &) = delete;
    StringSearchHelper & operator =(const StringSearchHelper &) = delete;
    ~StringSearchHelper();
    bool isMatch(const char *src) const;
    bool isPrefix() const noexcept { return _isPrefix; }
    bool isRegex() const noexcept { return _isRegex; }
    bool isCased() const noexcept { return _isCased; }
    bool isFuzzy() const noexcept { return _isFuzzy; }
    const vespalib::Regex & getRegex() const noexcept { return _regex; }
    const vespalib::FuzzyMatcher & getFuzzyMatcher() const noexcept { return *_fuzzyMatcher; }
private:
    vespalib::Regex                _regex;
    std::unique_ptr<vespalib::FuzzyMatcher> _fuzzyMatcher;
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
