// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "writable_memory.h"

namespace vespalib {

/**
 * Interface used to abstract a destination for output data. Note that
 * the output data itself is owned by the object implementing this
 * interface.
 **/
struct Output
{
    /**
     * Reserve space for more output data. The returned WritableMemory
     * must contain at least the requested number of bytes.
     *
     * @return the reserved output data
     * @param bytes number of bytes to reserve
     **/
    virtual WritableMemory reserve(size_t bytes) = 0;

    /**
     * Commit produced output data. Never commit more data than you
     * have reserved.
     *
     * @return this object, for chaining
     * @param bytes number of bytes to commit
     **/
    virtual Output &commit(size_t bytes) = 0;

    virtual ~Output();
};

} // namespace vespalib
