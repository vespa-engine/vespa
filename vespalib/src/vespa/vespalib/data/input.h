// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "memory.h"

namespace vespalib {

/**
 * Interface used to abstract a source of input data. Note that the
 * input data itself is owned by the object implementing this
 * interface.
 **/
struct Input
{
    /**
     * Obtain more input data. An empty Memory should be returned if
     * and only if all input data has been exhausted.
     *
     * @return the obtained input data
     **/
    virtual Memory obtain() = 0;

    /**
     * Evict processed input data. Never evict more data than you have
     * obtained.
     *
     * @return this object, for chaining
     * @param bytes the number of bytes to evict
     **/
    virtual Input &evict(size_t bytes) = 0;

    virtual ~Input();
};

} // namespace vespalib
