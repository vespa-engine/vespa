// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include "querynode.h"
#include <vespa/vespalib/text/lowercase.h>

namespace juniper {

/**
 * This registry is responsible for knowing the set of query terms that are marked as special tokens.
 * The class operates on a character stream and tries to tokenize this into special tokens.
 */
class SpecialTokenRegistry
{
public:
    /**
     * Helper class for handling a character stream.
     */
    class CharStream {
    private:
        const char * _srcBuf; // the current start of the source buffer
        const char * _srcItr; // the source iterator
        const char * _srcEnd; // the end of the source buffer
        const char * _nextStart; // the next start character
        ucs4_t * _dstBuf; // the start of the destination buffer
        ucs4_t * _dstItr; // the destination iterator
        ucs4_t * _dstEnd; // the end of the destination buffer
        bool _isStartWordChar;

    public:
        CharStream(const char * srcBuf, const char * srcEnd,
                   ucs4_t * dstBuf, ucs4_t * dstEnd);
        bool hasMoreChars() const { return _srcItr < _srcEnd; }
        bool hasMoreSpace() const { return _dstItr < _dstEnd; }
        ucs4_t getNextChar() {
            ucs4_t ch = Fast_UnicodeUtil::GetUTF8Char(_srcItr);
            ch = vespalib::LowerCase::convert(ch);
            *_dstItr++ = ch;
            return ch;
        }
        void reset() { _srcItr = _srcBuf; _dstItr = _dstBuf; }
        bool resetAndInc();
        bool isStartWordChar() const { return _isStartWordChar; }
        size_t getNumChars() const { return _dstItr - _dstBuf; }
        const char * getSrcStart() const { return _srcBuf; }
        const char * getSrcItr() const { return _srcItr; }
    };

private:
    std::vector<QueryTerm *> _specialTokens;

    bool match(const ucs4_t * qsrc, const ucs4_t * qend, CharStream & stream) const;

public:
    SpecialTokenRegistry(QueryExpr * query);
    const std::vector<QueryTerm *> & getSpecialTokens() const { return _specialTokens; }
    void addSpecialToken(QueryTerm * term) {
        _specialTokens.push_back(term);
    }
    /**
     * Tries to tokenize the given utf-8 buffer (character stream) into a special token.
     * Returns the new position of the buffer if a special token is matched, NULL otherwise.
     *
     * @param buf       start position of the utf-8 buffer.
     * @param bufend    end position of the utf-8 buffer.
     * @param dstbuf    start position of the destination ucs4 buffer where the characters are copied into.
     * @param dstend    end position of the destination ucs4 buffer.
     * @param origstart buffer start position of the token returned.
     * @param tokenlen  number of ucs4 characters in the returned token.
     * @return new buffer position (after token) or NULL.
     */
    const char * tokenize(const char * buf, const char * bufend,
                          ucs4_t * dstbuf, ucs4_t * dstbufend,
                          const char * & origstart, size_t & tokenlen) const;
};

} // namespace juniper

