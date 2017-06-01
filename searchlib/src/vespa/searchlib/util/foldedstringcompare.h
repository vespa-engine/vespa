// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

namespace search {

class FoldedStringCompare
{
public:
    FoldedStringCompare() {}

    /**
     * count number of UCS-4 characters in utf8 string
     *
     * @param key       NUL terminated utf8 string
     * @return integer  number of symbols in utf8 string before NUL
     */
    size_t size(const char *key) const;

    /**
     * Compare utf8 key with utf8 other key after folding both
     *
     * @param key       NUL terminated utf8 string
     * @param okey      NUL terminated utf8 string
     * @return integer   -1 if key < okey, 0 if key == okey, 1 if key > okey
     **/
    int compareFolded(const char *key, const char *okey) const;

    /**
     * Compare utf8 key with utf8 other key after folding both.
     *
     * @param key         NUL terminated utf8 string
     * @param okey        NUL terminated utf8 string
     * @param prefixLen   max number of symbols to compare before
     *                    considering keys identical.
     *
     * @return integer   -1 if key < okey, 0 if key == okey, 1 if key > okey
     */
    int compareFoldedPrefix(const char *key,
                            const char *okey,
                            size_t prefixLen) const;

    /*
     * Compare utf8 key with utf8 other key after folding both, if
     * they seem equal then fall back to comparing without folding.
     *
     * @param key       NUL terminated utf8 string
     * @param okey      NUL terminated utf8 string
     * @return integer   -1 if key < okey, 0 if key == okey, 1 if key > okey
     */
    int compare(const char *key, const char *okey) const;
};

} // namespace search