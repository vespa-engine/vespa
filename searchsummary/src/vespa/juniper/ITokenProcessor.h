// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
#include <vespa/fastlib/text/unicodeutil.h>

/** Implement this interface for objects that intend to serve as processing stages in
 *  a Juniper token processor pipeline.
 */

class ITokenProcessor
{
public:
    /** Token definition. Note that not all information might be available at all stages.
     *  As a minimum token, bytepos, wordpos and bytelen should have been set.
     *  Other fields should have been set to 0 and should be left untouched if not provided
     *  by the processor.
     */
    struct Token
    {
        Token() : token(NULL), bytepos(0), charpos(0), wordpos(0),
                  bytelen(0), charlen(0), curlen(0)
        {}
        const ucs4_t* token; //!< a normalized UCS4 representation of the token
        off_t bytepos;     //!< Position in bytes from start of original text
        off_t charpos;     //!< Position in number of characters according to utf8 encoding
        off_t wordpos;     //!< Position in number of words
        int bytelen;       //!< Size in bytes of the original token as in the text
        int charlen;       //!< Size in number of utf8 characters
        int curlen;        //!< Size in ucs4_t of the token after conversions

        Token(const Token& other) : token(other.token),
                                    bytepos(other.bytepos),
                                    charpos(other.charpos),
                                    wordpos(other.wordpos),
                                    bytelen(other.bytelen),
                                    charlen(other.charlen),
                                    curlen(other.curlen) {}
        Token& operator= (const Token& other) {
            token = other.token;
            bytepos = other.bytepos;
            charpos = other.charpos;
            wordpos = other.wordpos;
            bytelen = other.bytelen;
            charlen = other.charlen;
            curlen = other.curlen;
            return *this;
        }
    };

    virtual ~ITokenProcessor() {}

    /** handle the next token
     *  @param token The token to process.
     */
    virtual void handle_token(Token& token) = 0;

    /** handle the end of the text as a special, zero length token.
     *  @param token The token to process.
     */
    virtual void handle_end(Token& token) = 0;
};


