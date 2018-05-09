// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::docsummary {

/**
 * Interface for a tokenizer.
 */
class ITokenizer
{
public:
    /**
     * Representation of a token with type and text and optional stemmed variant.
     */
    class Token
    {
    public:
        enum Type {
            WORD,        // Fast_UnicodeUtil::IsWordChar() returns true
            NON_WORD,    // Fast_UnicodeUtil::IsWordChar() returns false
            PUNCTUATION, // Fast_UnicodeUtil::IsTerminalPunctuationChar() returns true
            ANNOTATION,  // Interlinear annotation
            NOT_DEF
        };
    private:
        vespalib::stringref _text;
        vespalib::stringref _stem;
        Type                _type;

    public:
        Token(const char * textBegin, const char * textEnd, Type type) :
            _text(textBegin, textEnd - textBegin), _stem(), _type(type) {}
        Token(const char * textBegin, const char * textEnd, const char * stemBegin, const char * stemEnd, Type type) :
            _text(textBegin, textEnd - textBegin), _stem(stemBegin, stemEnd - stemBegin), _type(type) {}
        const vespalib::stringref & getText() const { return _text; }
        const vespalib::stringref & getStem() const { return _stem; }
        bool hasStem() const { return _stem.c_str() != NULL; }
        Type getType() const { return _type; }
    };

    virtual ~ITokenizer() {}

    /**
     * Reset the tokenizer using the given buffer.
     */
    virtual void reset(const char * buf, size_t len) = 0;

    /**
     * Returns the size of the underlying buffer.
     */
    virtual size_t getBufferSize() const = 0;

    /**
     * Returns true if the text buffer has more tokens.
     */
    virtual bool hasMoreTokens() = 0;

    /**
     * Returns the next token from the text buffer.
     */
    virtual Token getNextToken() = 0;
};

}

