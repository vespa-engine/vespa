// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "itokenizer.h"
#include <vespa/fastlib/text/unicodeutil.h>

namespace search::docsummary {

/**
 * This class is used to tokenize an utf-8 text buffer into tokens of type
 * WORD, NON_WORD, PUNCTUATION, and ANNOTATION.
 *
 * Functions in Fast_UnicodeUtil are used to determine word characters and terminal punctuation characters.
 * The unit separator 0x1F is always treated as a token separator. The unit separator itself is not returned as a token.
 * Interlinear annotation (0xFFF9 original 0xFFFA stemmed 0xFFFB) is used to specify the stemmed variant of a word.
 * The annotation characters are not returned as part of a token.
 */
class Tokenizer : public ITokenizer
{
private:
    const char * _pos;   // the current position in the input buffer
    const char * _begin; // the begin of input buffer
    const char * _end;   // the end of the input buffer
    const char * _tokenBegin; // the start of the next token
    Token::Type  _type;  // the type of the current position
    bool         _hasMoreTokens; // do we have more tokens

    Token::Type getTokenType(ucs4_t ch) const;

public:
    /**
     * Creates a new tokenizer for the given utf-8 text buffer.
     */
    Tokenizer(const char * buf, size_t len);

    void reset(const char * buf, size_t len) override;
    size_t getBufferSize() const override { return _end - _begin; }
    bool hasMoreTokens() override;
    Token getNextToken() override;
};

}
