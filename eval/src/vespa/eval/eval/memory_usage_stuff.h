// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/memoryusage.h>
#include <vector>

namespace vespalib::eval {

template <typename T>
MemoryUsage self_memory_usage() { return MemoryUsage(sizeof(T), sizeof(T), 0, 0); }

template <typename V>
MemoryUsage vector_extra_memory_usage(const V &vec) {
    using T = typename V::value_type;
    MemoryUsage usage;
    usage.incAllocatedBytes(sizeof(T) * vec.capacity());
    usage.incUsedBytes(sizeof(T) * vec.size());
    return usage;
}

}
