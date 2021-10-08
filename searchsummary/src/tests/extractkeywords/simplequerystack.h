// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "simplequerystackitem.h"
#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/vespalib/stllike/string.h>

namespace search {

/**
 * A stack of SimpleQueryStackItems.
 *
 * A simple stack consisting of a list of SimpleQueryStackItems.
 * It is able to generate a binary encoding of itself
 * to a RawBuf.
 */
class SimpleQueryStack
{
private:
    /** The top of the stack. */
    SimpleQueryStackItem *_stack;

public:
    SimpleQueryStack(const SimpleQueryStack &) = delete;
    SimpleQueryStack& operator=(const SimpleQueryStack &) = delete;
    /**
     * Constructor for SimpleQueryStack.
     */
    SimpleQueryStack();
    /**
     * Destructor for SimpleQueryStack.
     */
    ~SimpleQueryStack();
    /**
     * Push an item on the stack.
     * @param item The SimpleQueryStackItem to push.
     */
    void Push(SimpleQueryStackItem *item);

    /**
     * Encode the contents of the stack in a binary buffer.
     * @param buf Pointer to a buffer containing the encoded contents.
     */
    void AppendBuffer(RawBuf *buf) const;
};

} // namespace search

