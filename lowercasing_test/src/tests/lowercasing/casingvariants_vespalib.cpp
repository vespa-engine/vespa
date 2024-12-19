// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/text/utf8.h>
#include <fstream>
#include <iostream>
#include <cassert>

using vespalib::LowerCase;
using vespalib::Utf8ReaderForZTS;
using vespalib::Utf8Writer;

uint32_t
getUCS4Char(const char *src)
{
    const char *input = src;
    Utf8ReaderForZTS reader(src);
    uint32_t result = reader.getChar();
    if (result != 0) {
        uint32_t extra = reader.getChar();
        if (extra != 0) {
            fprintf(stderr, "Warning: extra character from '%s' -> U+%04x U+%04X\n",
                    input, result, extra);
        }
        // mangle two characters into one fake UCS4 number
        // (in theory we should compare vector<UCS4>, but this is good enough)
        result |= (extra << 16);
    }
    return result;
}

std::string
getUTF8String(uint32_t ucs4Char)
{
    std::string target;
    Utf8Writer writer(target);
    writer.putChar(ucs4Char);
    return target;
}

int
main(int argc, char ** argv)
{
    assert(argc == 3);
    (void) argc;
    std::ifstream input(argv[1]);
    std::ifstream ref(argv[2]);
    char inputBuf[128];
    char refBuf[128];
    while (input.good()) {
        input.getline(inputBuf, 128);
        ref.getline(refBuf, 128);
        uint32_t inputChar = getUCS4Char(inputBuf);
        uint32_t refChar = getUCS4Char(refBuf);
        uint32_t lowerChar = LowerCase::convert(inputChar);
        if (refChar != lowerChar) {
            printf("input(%s,%u,0x%X), lower(%s,%u,0x%X), ref(%s,%u,0x%X) \n",
                   inputBuf, inputChar, inputChar,
                   getUTF8String(lowerChar).c_str(), lowerChar, lowerChar,
                   refBuf, refChar, refChar);
        }
    }
    input.close();
    return 0;
}
