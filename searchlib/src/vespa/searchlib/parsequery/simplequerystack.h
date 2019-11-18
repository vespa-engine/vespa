// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/vespalib/stllike/string.h>

namespace search {

/**
 * A stack of ParseItems.
 *
 * A simple stack consisting of a list of ParseItems.
 * It is able to generate a binary encoding of itself
 * to a search::RawBuf.
 */
class SimpleQueryStack
{
private:
    /** The number of items on the stack. */
    uint32_t _numItems;

    /** The top of the stack.
     * Warning: FastQT_ProximityEmul currently assumes this is the head
     * of a singly linked list (linked with _next).
     */
    search::ParseItem *_stack;

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
     * @param item The search::ParseItem to push.
     */
    void Push(search::ParseItem *item);


    /**
     * Encode the contents of the stack in a binary buffer.
     * @param buf Pointer to a buffer containing the encoded contents.
     */
    void AppendBuffer(search::RawBuf *buf) const;

    /**
     * Return the number of items on the stack.
     * @return The number of items on the stack.
     */
    uint32_t GetSize();
    /**
     * Set the number of items on the stack.
     * This can be used by QTs that change the stack
     * under the hood. Use with care!
     * @param numItems The number of items on the stack.
     */
    void SetSize(uint32_t numItems) { _numItems = numItems; }
};

} // namespace search

