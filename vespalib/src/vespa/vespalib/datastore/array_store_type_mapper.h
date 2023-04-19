// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace vespalib::datastore {

/*
 * This class provides mapping between type ids and array sizes needed for
 * storing a value with size smaller than or equal to the array size.
 *
 * The array sizes vector is a monotonic strictly increasing sequence that might end
 * with exponential growth.
 */
class ArrayStoreTypeMapper
{
protected:
    std::vector<size_t> _array_sizes;
public:
    ArrayStoreTypeMapper();
    ~ArrayStoreTypeMapper();

    uint32_t get_type_id(size_t array_size) const;
    size_t get_array_size(uint32_t type_id) const;
    uint32_t get_max_small_array_type_id(uint32_t max_small_array_type_id) const noexcept;
};

}
