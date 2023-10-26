// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vbench {

/**
 * Something that can be closed to disrupt its steady-state behavior.
 **/
struct Closeable
{
    virtual void close() = 0;
    virtual ~Closeable() {}
};

} // namespace vbench

