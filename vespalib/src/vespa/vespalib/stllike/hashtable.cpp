// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/stllike/hashtable.hpp>
#include <algorithm>

namespace {

constexpr unsigned long STL_PRIME_LIST[] =
{
            7ul,         7ul,          7ul,         17ul,         53ul,         97ul,        193ul,         389ul,
          769ul,      1543ul,       3079ul,       6151ul,      12289ul,      24593ul,      49157ul,       98317ul,
       196613ul,     393241ul,     786433ul,    1572869ul,    3145739ul,    6291469ul,   12582917ul,   25165843ul,
     50331653ul,  100663319ul,  201326611ul,  402653189ul,  805306457ul, 1610612741ul, 3221225473ul, 4294967291ul
};

}

namespace vespalib {

size_t
hashtable_base::getModuloStl(size_t size) noexcept
{
    if (size > 0xfffffffful) return 0xfffffffful;
    if (size < 8) return 7ul;
    uint32_t index = Optimized::msbIdx(size);
    return (size <= STL_PRIME_LIST[index - 1])
           ? STL_PRIME_LIST[index - 1]
           : STL_PRIME_LIST[index];
}

}
