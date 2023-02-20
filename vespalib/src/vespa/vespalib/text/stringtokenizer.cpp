// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stringtokenizer.h"

namespace {

class AsciiSet
{
public:
    explicit AsciiSet(vespalib::stringref s) {
        memset(_set, 0, sizeof(_set));
        for (char c : s) {
            add(c);
        }
    }
    [[nodiscard]] bool contains(uint8_t c) const {
        return _set[c];
    }
    void add(uint8_t c) {
        _set[c] = true;
    }
private:
    // For smaller memory footprint it might be changed with using 256 bits instead.
    bool _set[256];
};

using Token = vespalib::StringTokenizer::Token;
using TokenList = vespalib::StringTokenizer::TokenList;

/**
 * strip leading and trailing sequences
 * of characters contained in the strip set.
 **/
Token
stripString(vespalib::stringref source, const AsciiSet & strip)
{
    Token::size_type start = 0;
    while (start < source.size() && strip.contains(source[start])) {
        ++start;
    }
    Token::size_type stop = source.size();
    while (stop > start && strip.contains(source[stop - 1])) {
        --stop;
    }
    return source.substr(start, stop - start);
}

size_t
countSeparators(vespalib::stringref source, const AsciiSet & sep) {
    size_t count(0);
    for (char c : source) {
        if (sep.contains(c)) {
            count++;
        }
    }
    return count;
}

void
parse(TokenList& output, vespalib::stringref source, const AsciiSet & separators, const AsciiSet & strip)
{
    Token::size_type start = 0;
    for (Token::size_type i = 0; i < source.size(); ++i) {
        if (separators.contains(source[i])) {
            output.push_back(stripString(source.substr(start, i-start), strip));
            start = i+1;
        }
    }
    output.push_back(stripString(source.substr(start), strip));
    // Don't keep a single empty element
    if (output.size() == 1 && output[0].empty()) output.pop_back();
}

} // private namespace

namespace vespalib {

StringTokenizer::StringTokenizer(vespalib::stringref source,
                                 vespalib::stringref separators,
                                 vespalib::stringref strip)
    : _tokens()
{
    AsciiSet sep(separators);
    AsciiSet str(strip);
    _tokens.reserve(countSeparators(source, sep) + 1);
    parse(_tokens, source, sep, str);
}

StringTokenizer::~StringTokenizer() = default;

void
StringTokenizer::removeEmptyTokens()
{
    size_t emptyCount(0);
    for (const auto & token : _tokens) {
        if (token.empty()) emptyCount++;
    }
    if (emptyCount == 0) {
        return;
    }
    TokenList tokenlist;
    tokenlist.reserve(_tokens.size() - emptyCount);
    for (const auto & token : _tokens) {
        if (!token.empty()) tokenlist.push_back(token);
    }
    _tokens.swap(tokenlist);
}

}
