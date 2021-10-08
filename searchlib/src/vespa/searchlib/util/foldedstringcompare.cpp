// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "foldedstringcompare.h"
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/text/lowercase.h>

using vespalib::LowerCase;
using vespalib::Utf8ReaderForZTS;
namespace search {

size_t
FoldedStringCompare::
size(const char *key)
{
    return Utf8ReaderForZTS::countChars(key);
}

int
FoldedStringCompare::
compareFolded(const char *key, const char *okey)
{
    Utf8ReaderForZTS kreader(key);
    Utf8ReaderForZTS oreader(okey);

    for (;;) {
        uint32_t kval = LowerCase::convert(kreader.getChar());
        uint32_t oval = LowerCase::convert(oreader.getChar());

        if (kval != oval) {
            if (kval < oval) {
                return -1;
            } else {
                return 1;
            }
        }
        if (kval == 0) {
            return 0;
        }
    }
}


int
FoldedStringCompare::
compareFoldedPrefix(const char *key, const char *okey, size_t prefixLen)
{
    Utf8ReaderForZTS kreader(key);
    Utf8ReaderForZTS oreader(okey);

    for (size_t j = 0; j < prefixLen; ++j ) {
        uint32_t kval = LowerCase::convert(kreader.getChar());
        uint32_t oval = LowerCase::convert(oreader.getChar());

        if (kval != oval) {
            if (kval < oval) {
                return -1;
            } else {
                return 1;
            }
        }
        if (kval == 0) return 0;
    }
    // reached end of prefix
    return 0;
}

int
FoldedStringCompare::
comparePrefix(const char *key, const char *okey, size_t prefixLen)
{
    int res = compareFoldedPrefix(key, okey, prefixLen);
    if (res != 0) return res;
    return strncmp(key, okey, prefixLen);
}


int
FoldedStringCompare::
compare(const char *key, const char *okey)
{
    int res = compareFolded(key, okey);
    if (res != 0) return res;
    return strcmp(key, okey);
}

} // namespace search

