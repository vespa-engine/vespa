// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>
#include <functional>
#include <vector>

namespace search {

namespace detail {

template <typename T>
concept FoldableString = std::same_as<const char*,T> || std::same_as<std::reference_wrapper<const std::vector<uint32_t>>, T>;

}

class FoldedStringCompare
{
public:
    /**
     * count number of UCS-4 characters in UTF-8 string
     *
     * @param key       NUL terminated UTF-8 string
     * @return integer  number of symbols in UTF-8 string before NUL
     */
    static size_t size(const char *key);

    /**
     * Compare UTF-8 key with UTF-8 other key after folding both
     *
     * @param key       NUL terminated UTF-8 string or vector<uint32_t>
     * @param okey      NUL terminated UTF-8 string or vector<uint32_t>
     * @return integer   -1 if key < okey, 0 if key == okey, 1 if key > okey
     **/
    template <bool fold_lhs, bool fold_rhs, detail::FoldableString KeyType, detail::FoldableString OKeyType>
    static int compareFolded(KeyType key, OKeyType okey);

    /**
     * Compare UTF-8 key with UTF-8 other key after folding both.
     *
     * @param key         NUL terminated UTF-8 string
     * @param okey        NUL terminated UTF-8 string
     * @param prefixLen   max number of symbols to compare before
     *                    considering keys identical.
     *
     * @return integer   -1 if key < okey, 0 if key == okey, 1 if key > okey
     */
    template <bool fold_lhs, bool fold_rhs>
    static int compareFoldedPrefix(const char *key, const char *okey, size_t prefixLen);

    /*
     * Compare UTF-8 key with UTF-8 other key after folding both, if
     * they seem equal then fall back to comparing without folding.
     *
     * @param key       NUL terminated UTF-8 string
     * @param okey      NUL terminated UTF-8 string
     * @return integer   -1 if key < okey, 0 if key == okey, 1 if key > okey
     */
    static int compare(const char *key, const char *okey);

    /*
     * Compare UTF-8 key with UTF-8 other key after folding both for prefix, if
     * they seem equal then fall back to comparing without folding.
     *
     * @param key         NUL terminated UTF-8 string
     * @param okey        NUL terminated UTF-8 string
     * @param prefixLen   max number of symbols to compare before
     *                    considering keys identical.
     * @return integer   -1 if key < okey, 0 if key == okey, 1 if key > okey
     */
    static int comparePrefix(const char *key, const char *okey, size_t prefixLen);
};

} // namespace search
