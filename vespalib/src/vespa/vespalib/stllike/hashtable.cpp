// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/stllike/hashtable.hpp>

namespace {

static const unsigned long __stl_prime_list[] =
{
            7ul,        17ul,         53ul,         97ul,        193ul,
          389ul,       769ul,       1543ul,       3079ul,       6151ul,
        12289ul,     24593ul,      49157ul,      98317ul,     196613ul,
       393241ul,    786433ul,    1572869ul,    3145739ul,    6291469ul,
     12582917ul,  25165843ul,   50331653ul,  100663319ul,  201326611ul,
    402653189ul, 805306457ul, 1610612741ul, 3221225473ul, 4294967291ul
};

static const unsigned long __simple_modulator_list[] =
{
                                                    0x8ul,
         0x10ul,       0x20ul,       0x40ul,       0x80ul,
        0x100ul,      0x200ul,      0x400ul,      0x800ul,
       0x1000ul,     0x2000ul,     0x4000ul,     0x8000ul,
      0x10000ul,    0x20000ul,    0x40000ul,    0x80000ul,
     0x100000ul,   0x200000ul,   0x400000ul,   0x800000ul,
    0x1000000ul,  0x2000000ul,  0x4000000ul,  0x8000000ul,
   0x10000000ul, 0x20000000ul, 0x40000000ul, 0x80000000ul,
  0x100000000ul
};

}

namespace vespalib {

size_t
hashtable_base::getModulo(size_t newSize, const unsigned long * list, size_t sz)
{
    const unsigned long* first = list;
    const unsigned long* last = list + sz;
    const unsigned long* pos = std::lower_bound(first, last, newSize);
    return (pos == last) ? *(last - 1) : *pos;
}

size_t
hashtable_base::getModuloStl(size_t newSize)
{
    return getModulo(newSize, __stl_prime_list, sizeof(__stl_prime_list)/sizeof(__stl_prime_list[0]));
}

size_t
hashtable_base::getModuloSimple(size_t newSize)
{
    return getModulo(newSize, __simple_modulator_list, sizeof(__simple_modulator_list)/sizeof(__simple_modulator_list[0]));
}

}
