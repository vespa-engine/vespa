// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vbench {

/**
 * Simple wrapper referencing a writable region of memory. This class
 * does not have ownership of the referenced memory region.
 **/
struct WritableMemory {
    char   *data;
    size_t  size;
    WritableMemory() : data(0), size(0) {}
    WritableMemory(char *d, size_t s) : data(d), size(s) {}
};

} // namespace vbench

