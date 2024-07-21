// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/stllike/hashtable.hpp>
#include <algorithm>

namespace {

static const size_t __chunk_size{8};
static const unsigned long __stl_prime_list[][__chunk_size] =
{
  {        7ul,        17ul,        53ul,         97ul,        193ul,        389ul,        769ul,       1543ul},
  {     3079ul,      6151ul,     12289ul,      24593ul,      49157ul,      98317ul,     196613ul,     393241ul},
  {   786433ul,   1572869ul,   3145739ul,    6291469ul,   12582917ul,   25165843ul,   50331653ul,  100663319ul},
  {201326611ul, 402653189ul, 805306457ul, 1610612741ul, 3221225473ul, 4294967291ul, 4294967291ul, 4294967291ul},
};

static const unsigned long __upper_bound_list[] = {1543ul, 393241ul, 100663319ul, 4294967291ul};
static const size_t __upper_bound_list_size = sizeof(__upper_bound_list)/sizeof(__upper_bound_list[0]);
}

namespace vespalib {

size_t
hashtable_base::getModuloStl(size_t size) noexcept
{
  for(size_t i = 0; i < __upper_bound_list_size; ++i) {
    if(size <= __upper_bound_list[i]) {
      for(size_t j = 0; j < __chunk_size; ++j) {
        if(__stl_prime_list[i][j] >= size) return __stl_prime_list[i][j];
      }
    }
  }
  return __upper_bound_list[__upper_bound_list_size - 1];
}

}
