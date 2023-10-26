// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastlib/text/normwordfolder.h>
#include <cassert>
#include <fstream>
#include <iostream>

ucs4_t
getUCS4Char(const char *src)
{
    return Fast_UnicodeUtil::GetUTF8Char(src);
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
        ucs4_t lowerChar = wordFolder.ToFold(inputChar);
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

