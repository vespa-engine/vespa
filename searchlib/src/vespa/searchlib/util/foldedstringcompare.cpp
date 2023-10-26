// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "foldedstringcompare.h"
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/text/lowercase.h>

using vespalib::LowerCase;
using vespalib::Utf8ReaderForZTS;
namespace search {

using Utf32VectorRef = std::reference_wrapper<const std::vector<uint32_t>>;

namespace foldedstringcompare {

class Utf32Reader {
    using Iterator = typename std::vector<uint32_t>::const_iterator;

    Iterator _cur;
    Iterator _end;
public:
    Utf32Reader(const std::vector<uint32_t>& key)
        : _cur(key.begin()),
          _end(key.end())
    {
    }

    bool hasMore() const noexcept { return _cur != _end; }
    uint32_t getChar() noexcept { return *_cur++; }
};

template <typename T> class FoldableStringHelper;

template <> class FoldableStringHelper<const char*>
{
public:
    using Reader = Utf8ReaderForZTS;
};

template <> class FoldableStringHelper<Utf32VectorRef>
{
public:
    using Reader = Utf32Reader;
};

}

template <typename KeyType>
using Reader = typename foldedstringcompare::FoldableStringHelper<KeyType>::Reader;

size_t
FoldedStringCompare::
size(const char *key)
{
    return Utf8ReaderForZTS::countChars(key);
}

template <bool fold_lhs, bool fold_rhs, detail::FoldableString KeyType, detail::FoldableString OKeyType>
int
FoldedStringCompare::
compareFolded(KeyType key, OKeyType okey)
{
    Reader<KeyType> kreader(key);
    Reader<OKeyType> oreader(okey);

    for (;;) {
        if (!kreader.hasMore()) {
            return oreader.hasMore() ? -1 : 0;
        } else if (!oreader.hasMore()) {
            return 1;
        }
        uint32_t kval = fold_lhs ? LowerCase::convert(kreader.getChar()) : kreader.getChar();
        uint32_t oval = fold_rhs ? LowerCase::convert(oreader.getChar()) : oreader.getChar();

        if (kval != oval) {
            if (kval < oval) {
                return -1;
            } else {
                return 1;
            }
        }
    }
}

template <bool fold_lhs, bool fold_rhs>
int
FoldedStringCompare::
compareFoldedPrefix(const char *key, const char *okey, size_t prefixLen)
{
    Utf8ReaderForZTS kreader(key);
    Utf8ReaderForZTS oreader(okey);

    for (size_t j = 0; j < prefixLen; ++j ) {
        uint32_t kval = fold_lhs ? LowerCase::convert(kreader.getChar()) : kreader.getChar();
        uint32_t oval = fold_rhs ? LowerCase::convert(oreader.getChar()) : oreader.getChar();

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
    // reached end of prefix
    return 0;
}

int
FoldedStringCompare::
comparePrefix(const char *key, const char *okey, size_t prefixLen)
{
    int res = compareFoldedPrefix<true, true>(key, okey, prefixLen);
    if (res != 0) {
        return res;
    }
    return compareFoldedPrefix<false, false>(key, okey, prefixLen);
}


int
FoldedStringCompare::
compare(const char *key, const char *okey)
{
    int res = compareFolded<true, true>(key, okey);
    if (res != 0) {
        return res;
    }
    return strcmp(key, okey);
}

template int FoldedStringCompare::compareFolded<false, false>(const char* key, Utf32VectorRef okey);
template int FoldedStringCompare::compareFolded<true, false>(const char* key, Utf32VectorRef okey);
template int FoldedStringCompare::compareFolded<false, false>(Utf32VectorRef key, const char* okey);
template int FoldedStringCompare::compareFolded<false, true>(Utf32VectorRef key, const char* okey);

template int FoldedStringCompare::compareFolded<false, false>(const char* key, const char* okey);
template int FoldedStringCompare::compareFolded<false, true>(const char* key, const char* okey);
template int FoldedStringCompare::compareFolded<true, false>(const char* key, const char* okey);
template int FoldedStringCompare::compareFolded<true, true>(const char* key, const char* okey);

template int FoldedStringCompare::compareFoldedPrefix<false, false>(const char* key, const char* okey, size_t prefixLen);
template int FoldedStringCompare::compareFoldedPrefix<false, true>(const char* key, const char* okey, size_t prefixLen);
template int FoldedStringCompare::compareFoldedPrefix<true, false>(const char* key, const char* okey, size_t prefixLen);
template int FoldedStringCompare::compareFoldedPrefix<true, true>(const char* key, const char* okey, size_t prefixLen);

} // namespace search

