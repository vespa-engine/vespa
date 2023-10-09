// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

namespace vespalib {

/**
 * Simple wrapper referencing a writable region of memory. This class
 * does not have ownership of the referenced memory region.
 **/
struct WritableMemory {
    char   *data;
    size_t  size;
    WritableMemory() : data(nullptr), size(0) {}
    WritableMemory(char *d, size_t s) : data(d), size(s) {}
};

} // namespace vespalib
