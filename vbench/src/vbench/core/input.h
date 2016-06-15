// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "memory.h"

namespace vbench {

/**
 * Interface used to abstract a source of input data. Note that the
 * input data itself is owned by the object implementing this
 * interface.
 **/
struct Input
{
    /**
     * Obtain more input data. You will never obtain more data than
     * requested, but you may obtain less.
     *
     * @return the obtained input data
     * @param bytes the number of bytes requested
     * @param lowMark minimum bytes in byffer before refilling
     **/
    virtual Memory obtain(size_t bytes, size_t lowMark) = 0;

    /**
     * Evict processed input data. Never evict more data than you have
     * obtained.
     *
     * @return this object, for chaining
     * @param bytes the number of bytes to evict
     **/
    virtual Input &evict(size_t bytes) = 0;

    virtual ~Input() {}
};

} // namespace vbench

