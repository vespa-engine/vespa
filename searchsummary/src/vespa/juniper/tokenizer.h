// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "specialtokenregistry.h"
#include "ITokenProcessor.h"

class Fast_WordFolder;

#define TOKEN_DSTLEN 1024

class JuniperTokenizer
{
public:
    JuniperTokenizer(const Fast_WordFolder* wordfolder,
                     const char* text, size_t len, ITokenProcessor* = NULL,
                     const juniper::SpecialTokenRegistry * registry = NULL);
    inline void SetSuccessor(ITokenProcessor* successor) { _successor = successor; }
    void setRegistry(const juniper::SpecialTokenRegistry * registry) { _registry = registry; }

    void SetText(const char* text, size_t len);

    // Scan the input and dispatch to the successor
    void scan();
private:
    const Fast_WordFolder* _wordfolder;
    const char* _text;  // The current input text
    size_t _len;        // Length of the text input
    ITokenProcessor* _successor;
    const juniper::SpecialTokenRegistry * _registry;
    off_t _charpos;  // Last utf8 character position
    off_t _wordpos;  // Offset in numbering of words compared to input (as result of splits)
    ucs4_t _buffer[TOKEN_DSTLEN];  // Temp. buffer to store folding result
private:
    JuniperTokenizer(const JuniperTokenizer&);
    JuniperTokenizer& operator=(const JuniperTokenizer&);
};


