// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hash_map.hpp"
#include "hash_map_equal.hpp"
#include <vespa/vespalib/util/array_equal.hpp>

namespace vespalib {
}

VESPALIB_HASH_MAP_INSTANTIATE(vespalib::string, vespalib::string);
VESPALIB_HASH_MAP_INSTANTIATE(vespalib::string, int);
VESPALIB_HASH_MAP_INSTANTIATE(vespalib::string, unsigned int);
VESPALIB_HASH_MAP_INSTANTIATE(vespalib::string, unsigned long);
VESPALIB_HASH_MAP_INSTANTIATE(vespalib::string, unsigned long long);
VESPALIB_HASH_MAP_INSTANTIATE(vespalib::string, double);
VESPALIB_HASH_MAP_INSTANTIATE(int64_t, int32_t);
VESPALIB_HASH_MAP_INSTANTIATE(int64_t, uint32_t);
VESPALIB_HASH_MAP_INSTANTIATE(int32_t, uint32_t);
VESPALIB_HASH_MAP_INSTANTIATE(uint16_t, uint16_t);
VESPALIB_HASH_MAP_INSTANTIATE(uint16_t, uint32_t);
VESPALIB_HASH_MAP_INSTANTIATE(uint32_t, int32_t);
VESPALIB_HASH_MAP_INSTANTIATE(uint32_t, uint32_t);
VESPALIB_HASH_MAP_INSTANTIATE(uint64_t, uint32_t);
VESPALIB_HASH_MAP_INSTANTIATE(uint64_t, uint64_t);
VESPALIB_HASH_MAP_INSTANTIATE(uint64_t, bool);
VESPALIB_HASH_MAP_INSTANTIATE(double, uint32_t);
VESPALIB_HASH_MAP_INSTANTIATE(float, uint32_t);
