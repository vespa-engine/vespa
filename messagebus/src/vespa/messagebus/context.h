// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstring>
#include <cstdint>

namespace mbus {

/**
 * A context is an application specific unit of information that can
 * be attached to a routable. Specifically, messagebus will ensure
 * that when a reply is obtained, it will have the same context as the
 * original message.
 **/
struct Context
{
    /**
     * This is a region of memory that can be interpreted as either an
     * integer or a pointer.
     **/
    union {
        uint64_t UINT64;
        void    *PTR;
    } value;

    /**
     * Create a context that is set to 0, however you interpret it.
     **/
    Context() { memset(&value, 0, sizeof(value)); }

    /**
     * Create a contex from an integer.
     *
     * @param v the value
     **/
    Context(uint64_t v) { value.UINT64 = v; }

    /**
     * Create a context from a pointer
     *
     * @param pt the pointer
     **/
    Context(void *pt) { value.PTR = pt; }
};

} // namespace mbus

