// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "raw_buffer_type_mapper.h"
#include <vespa/vespalib/datastore/aligner.h>
#include <algorithm>
#include <cmath>
#include <limits>

using vespalib::datastore::Aligner;
using vespalib::datastore::ArrayStoreTypeMapper;

namespace search::attribute {

RawBufferTypeMapper::RawBufferTypeMapper()
    : ArrayStoreTypeMapper()
{
}

RawBufferTypeMapper::RawBufferTypeMapper(uint32_t max_small_buffer_type_id, double grow_factor)
    : ArrayStoreTypeMapper()
{
    Aligner<4> aligner;
    _array_sizes.reserve(max_small_buffer_type_id + 1);
    _array_sizes.emplace_back(0); // type id 0 uses LargeArrayBufferType<char>
    size_t array_size = 8u;
    for (uint32_t type_id = 1; type_id <= max_small_buffer_type_id; ++type_id) {
        if (type_id > 1) {
            array_size = std::max(array_size +  4, static_cast<size_t>(std::floor(array_size * grow_factor)));
            array_size = aligner.align(array_size);
        }
        if (array_size > std::numeric_limits<uint32_t>::max()) {
            break;
        }
        _array_sizes.emplace_back(array_size);
    }
}

RawBufferTypeMapper::~RawBufferTypeMapper() = default;

}
