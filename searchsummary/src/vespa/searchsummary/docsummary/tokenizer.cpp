// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tokenizer.h"
#include <cassert>

namespace search::docsummary {

Tokenizer::Token::Type
Tokenizer::getTokenType(ucs4_t ch) const
{
    if (Fast_UnicodeUtil::IsWordChar(ch)) {
        return Token::WORD;
    } else {
        if (Fast_UnicodeUtil::IsTerminalPunctuationChar(ch)) {
            return Token::PUNCTUATION;
        } else {
            return Token::NON_WORD;
        }
    }
}

Tokenizer::Tokenizer(const char * buf, size_t len) :
    _pos(buf),
    _begin(buf),
    _end(buf + len),
    _tokenBegin(buf),
    _type(Token::NOT_DEF),
    _hasMoreTokens(_pos < _end)
{
}

void
Tokenizer::reset(const char * buf, size_t len)
{
    _pos = buf;
    _begin = buf;
    _end = buf + len;
    _tokenBegin = buf;
    _type = Token::NOT_DEF;
    _hasMoreTokens = (_pos < _end);
}

bool
Tokenizer::hasMoreTokens()
{
    return _hasMoreTokens;
}

Tokenizer::Token
Tokenizer::getNextToken()
{
    const char * textBegin = _tokenBegin;
    const char * textEnd = _pos;
    const char * stemBegin = NULL;
    const char * stemEnd = NULL;
    const char * next = _pos;
    bool insideAnnotation = false;
    for (; _pos < _end; ) {
        ucs4_t ch;
        if (static_cast<unsigned char>(*next) < 0x80) {
            ch = *next++;
            if (ch == 0x1F) { // unit separator
                Token t(textBegin, textEnd, stemBegin, stemEnd, _type);
                _pos = next; // advance to next char
                _tokenBegin = next; // the next token begins at the next char
                _type = Token::NOT_DEF; // reset the token type
                if (_pos == _end) { // this is the last token
                    _hasMoreTokens = false;
                }
                return t;
            }
        } else {
            ch = Fast_UnicodeUtil::GetUTF8CharNonAscii(next); // updates next to the next utf8 character
            if (ch == 0xFFF9) { // anchor
                insideAnnotation = true;
                textBegin = next;
                _type = Token::ANNOTATION;
            }
        }
        if (!insideAnnotation) {
            Token::Type tmpType = getTokenType(ch);
            if (_type != Token::NOT_DEF && _type != tmpType) { // we found a new token type
                Token t(textBegin, textEnd, stemBegin, stemEnd, _type);
                _tokenBegin = _pos; // the next token begins at this char
                _pos = next; // advance to next char
                _type = tmpType; // remember the new token type
                return t;
            }
            _type = tmpType;
            textEnd = next; // advance to next char
        } else { // inside annotation
            if (ch == 0xFFFA) { // separator
                textEnd = _pos;
                stemBegin = next;
            } else if (ch == 0xFFFB && stemBegin != NULL) { // terminator
                stemEnd = _pos;
                insideAnnotation = false;
            }
        }

        _pos = next;
    }
    assert(_pos == _end);
    _hasMoreTokens = false;
    return Token(textBegin, _pos, _type); // return the last token
}

}
