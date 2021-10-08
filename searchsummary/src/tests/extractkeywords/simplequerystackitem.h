// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/weight.h>
#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchlib/parsequery/parse.h>

namespace search {

/**
 * An item on the simple query stack.
 *
 * An object of this class represents a single item
 * on the simple query stack. It has a type, which corresponds
 * to the different query stack execution operations. It also
 * provides an arity, and the string values indexName and term, to
 * accomodate the different needs of the operations.
 */
class SimpleQueryStackItem : public ParseItem
{
private:
    SimpleQueryStackItem(const SimpleQueryStackItem &) = delete;
    SimpleQueryStackItem& operator=(const SimpleQueryStackItem &) = delete;
    SimpleQueryStackItem();
public:
    /** Pointer to next item in a linked list. */
    SimpleQueryStackItem *_next;

private:
    uint32_t      _arg1;
    double        _arg2;
    double        _arg3;
    ItemType      _type;

public:
    ItemType Type() const { return _type; }

    /** The number of operands for the operation. */
    uint32_t _arity;
    /** The name of the specified index, or empty if no index. */
    vespalib::string _indexName;
    /** The specified search term. */
    vespalib::string  _term;

/**
 * Overloaded constructor for SimpleQueryStackItem. Used primarily for
 * the operators, or phrase without indexName.
 *
 * @param type The type of the SimpleQueryStackItem.
 * @param arity The arity of the operation indicated by the SimpleQueryStackItem.
 */
    SimpleQueryStackItem(ItemType type, int arity);

/**
 * Overloaded constructor for SimpleQueryStackItem. Used for PHRASEs.
 *
 * @param type The type of the SimpleQueryStackItem.
 * @param arity The arity of the operation indicated by the SimpleQueryStackItem.
 * @param idx The name of the index of the SimpleQueryStackItem.
 */
    SimpleQueryStackItem(ItemType type, int arity, const char *index);

/**
 * Overloaded constructor for SimpleQueryStackItem. Used for TERMs without index.
 *
 * @param type The type of the SimpleQueryStackItem.
 * @param term The actual term string of the SimpleQueryStackItem.
 */
    SimpleQueryStackItem(ItemType type, const char *term);

/**
 * Destructor for SimpleQueryStackItem.
 */
    ~SimpleQueryStackItem();

/**
 * Set the value of the _term field.
 * @param term The string to set the _term field to.
 */
    void SetTerm(const char *term) { _term = term; }

/**
 * Set the value of the _indexName field.
 * @param idx The string to set the _indexName field to.
 */
    void SetIndex(const char *index) { _indexName = index; }

    /**
     * Set the type of the operator. Use this with caution,
     * as this changes the semantics of the item.
     *
     * @param type The new type.
     */
    void SetType(ItemType type) {
        _type = type;
    }

    /**
     * Encode the item in a binary buffer.
     * @param buf Pointer to a buffer containing the encoded contents.
     */
    void AppendBuffer(RawBuf *buf) const;
};

}
