// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stringtokenizer.h"

namespace {

class AsciiSet
{
public:
    AsciiSet(const vespalib::stringref & s) {
        memset(_set, 0, sizeof(_set));
        for (size_t i(0), m(s.size()); i < m; i++) {
            add(s[i]);
        }
    }
    bool contains(uint8_t c) const {
        return _set[c];
    }
    void add(uint8_t c) {
        _set[c] = true;
    }
private:
    // For smaller memory footprint it might be changed with using 256 bits instead.
    bool _set[256];
};

typedef vespalib::StringTokenizer::Token Token;
typedef vespalib::StringTokenizer::TokenList TokenList;

/**
 * strip leading and trailing sequences
 * of characters contained in the strip set.
 **/
Token stripString(const vespalib::stringref & source,
                        const AsciiSet & strip)
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

void parse(TokenList& output,
           const vespalib::stringref & source,
           const AsciiSet & separators,
           const AsciiSet & strip)
{
    Token::size_type start = 0;
    for(Token::size_type i = 0; i < source.size(); ++i) {
        if (separators.contains(source[i])) {
            output.push_back(stripString(source.substr(start, i-start), strip));
            start = i+1;
        }
    }
    output.push_back(stripString(source.substr(start), strip));
    // Don't keep a single empty element
    if (output.size() == 1 && output[0].size() == 0) output.pop_back();
}

} // private namespace

namespace vespalib {

StringTokenizer::StringTokenizer(const vespalib::stringref & source,
                                 const vespalib::stringref & separators,
                                 const vespalib::stringref & strip)
    : _tokens()
{
    AsciiSet sep(separators);
    AsciiSet str(strip);
    parse(_tokens, source, sep, str);
}

void StringTokenizer::removeEmptyTokens()
{
    size_t emptyCount(0);
    for (TokenList::const_iterator it = _tokens.begin(); it != _tokens.end(); ++it) {
        if (it->empty()) emptyCount++;
    }
    if (emptyCount == 0) {
        return;
    }
    TokenList tokenlist;
    tokenlist.reserve(_tokens.size() - emptyCount);
    for (TokenList::const_iterator it = _tokens.begin(); it != _tokens.end(); ++it) {
        if (!it->empty()) tokenlist.push_back(*it);
    }
    _tokens.swap(tokenlist);
}

}
