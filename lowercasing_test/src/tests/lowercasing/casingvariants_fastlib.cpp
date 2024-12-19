// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastlib/text/normwordfolder.h>
#include <cassert>
#include <fstream>
#include <iostream>

ucs4_t
getUCS4Char(const char *src)
{
    const char *input = src;
    ucs4_t result = Fast_UnicodeUtil::GetUTF8Char(src);
    if (result != 0) {
        ucs4_t extra = Fast_UnicodeUtil::GetUTF8Char(src);
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

int
main(int argc, char ** argv)
{
    assert(argc == 3);
    (void) argc;
    std::ifstream input(argv[1]);
    std::ifstream ref(argv[2]);
    Fast_NormalizeWordFolder wordFolder;
    char inputBuf[128];
    char refBuf[128];
    char lowerBuf[128];
    while (input.good()) {
        input.getline(inputBuf, 128);
        ref.getline(refBuf, 128);
        ucs4_t inputChar = getUCS4Char(inputBuf);
        ucs4_t refChar = getUCS4Char(refBuf);
        ucs4_t lowerChar = wordFolder.lowercase_and_fold(inputChar);
        Fast_UnicodeUtil::utf8ncopy(lowerBuf, &lowerChar, 128, 1);
        if (refChar != lowerChar) {
            printf("input(%s,%u,0x%X), lower(%s,%u,0x%X), ref(%s,%u,0x%X) \n",
                   inputBuf, inputChar, inputChar,
                   lowerBuf, lowerChar, lowerChar,
                   refBuf, refChar, refChar);
        }
    }
    input.close();
    return 0;
}
