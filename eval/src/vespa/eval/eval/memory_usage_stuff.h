// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/memoryusage.h>
#include <vector>

namespace vespalib::eval {

template <typename T>
MemoryUsage self_memory_usage() { return MemoryUsage(sizeof(T), sizeof(T), 0, 0); }

template <typename T>
MemoryUsage vector_extra_memory_usage(const std::vector<T> &vec) {
    MemoryUsage usage;
    usage.incAllocatedBytes(sizeof(T) * vec.capacity());
    usage.incUsedBytes(sizeof(T) * vec.size());
    return usage;
}

}
