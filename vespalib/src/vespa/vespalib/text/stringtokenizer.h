// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @version $Id$
 * @author Haakon Humberset
 * @date 2005-09-23
 *
 * @brief String tokenizer with a C++ approach.
 *
 * Uses vespalib::string and common C++ functions. Gives a simple interface
 * to a string tokenizer, not necessarily the most efficient one.
 *
 * @class vespalib::StringTokenizer
 */

#pragma once

#include <vector>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

class StringTokenizer {
public:
    using Token = vespalib::stringref;
    using TokenList = std::vector<Token>;
    using Iterator = TokenList::const_iterator;

    /**
     * @brief Split source on separators with optional trim.
     *
     * Take the source string and split on each occurrence
     * of a character contained in seperators.
     * From the resulting tokens, remove leading and
     * trailing sequences of characters in the strip set.
     * As a special case, if the input contains only one
     * token and that token is empty (or empty after
     * removal of strip characters) the result is an empty
     * token list.
     *
     * @param source     The input string to be tokenized
     * @param separators The characters to be used as token separators
     * @param strip      Characters to be stripped from both ends of each token
     **/
    explicit StringTokenizer(vespalib::stringref source)
        : StringTokenizer(source, ",")
    {}
    StringTokenizer(vespalib::stringref source, vespalib::stringref separators)
        : StringTokenizer(source, separators, " \t\f\r\n")
    {}
    StringTokenizer(vespalib::stringref source,
                    vespalib::stringref separators,
                    vespalib::stringref strip);
    StringTokenizer(StringTokenizer &&) noexcept = default;
    StringTokenizer & operator=(StringTokenizer &&) noexcept = default;
    ~StringTokenizer();

    /** Remove any empty tokens from the token list */
    void removeEmptyTokens();

    /** How many tokens is in the current token list? */
    [[nodiscard]] unsigned int size() const { return _tokens.size(); }

    /** Access a token from the current token list */
    const Token & operator[](unsigned int index) const { return _tokens[index]; }

    [[nodiscard]] Iterator begin() const { return _tokens.begin(); }
    [[nodiscard]] Iterator end() const { return _tokens.end(); }

    /** Access the entire token list */
    [[nodiscard]] const TokenList & getTokens() const { return _tokens; }

private:
    TokenList _tokens;
};
}

