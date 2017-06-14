// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Creation date: 2000-05-15
 *
 * Declaration of the SimpleQueryStack
 *
 *   Copyright (C) 1997-2003 Fast Search & Transfer ASA
 *   Copyright (C) 2003 Overture Services Norway AS
 *               ALL RIGHTS RESERVED
 */
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
    SimpleQueryStack(const SimpleQueryStack &);
    SimpleQueryStack& operator=(const SimpleQueryStack &);

    static vespalib::string ReadString(const char *&p);
    static uint64_t ReadUint64(const char *&p);
    static uint64_t ReadCompressedPositiveInt(const char *&p);

    /** The number of items on the stack. */
    uint32_t _numItems;

    /** The top of the stack.
     * Warning: FastQT_ProximityEmul currently assumes this is the head
     * of a singly linked list (linked with _next).
     */
    search::ParseItem *_stack;

    /** Is this query OK for FirstPage? */
    bool _FP_queryOK;

public:
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
     * Pop an item of the stack.
     * @return Pointer to the search::ParseItem poped, or NULL if stack is empty.
     */
    search::ParseItem *Pop();
    /**
     * Top node of the stack.
     * @return Pointer to the top search::ParseItem, or NULL if stack is empty.
     */
    search::ParseItem *Top() { return _stack; }

    /**
     * Encode the contents of the stack in a binary buffer.
     * @param buf Pointer to a buffer containing the encoded contents.
     */
    void AppendBuffer(search::RawBuf *buf) const;

    size_t GetBufferLen() const;
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

    /**
     * Is it possible to run this query on FirstPage?
     * @return true if ok
     */
    bool _FP_isAllowed();
    /**
     * Make a string representation of the search::RawBuf representing a querystack.
     * @param theBuf The querystack encoded buffer.
     * @return a fresh string
     */
    static vespalib::string StackbufToString(const vespalib::stringref &theBuf);
};

} // namespace search

