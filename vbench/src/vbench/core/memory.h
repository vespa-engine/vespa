// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string.h"

namespace vbench {

/**
 * Simple wrapper referencing a read-only region of memory.
 **/
struct Memory
{
    const char *data;
    size_t      size;
    Memory() : data(0), size(0) {}
    Memory(const char *d, size_t s) : data(d), size(s) {}
    Memory(const char *str) : data(str), size(strlen(str)) {}
    Memory(const string &str) : data(str.data()), size(str.size()) {}
};

} // namespace vbench

