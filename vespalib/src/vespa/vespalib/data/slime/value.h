// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "type.h"
#include "cursor.h"

namespace vespalib {
namespace slime {

class SymbolTable;
struct SymbolLookup;
struct SymbolInserter;
struct ValueFactory;
struct ArrayTraverser;
struct ObjectTraverser;

/**
 * Top-level value class with default behavior for all virtual
 * functions.
 **/
class Value : public Cursor
{
protected:
    virtual ~Value() {}

    virtual Cursor &addLeaf(const ValueFactory &input);
    virtual Cursor &setLeaf(Symbol symbol, const ValueFactory &input);
    virtual Cursor &setLeaf(Memory name, const ValueFactory &input);

public:
    virtual bool valid() const;
    virtual Type type() const;
    virtual size_t children() const;
    virtual size_t entries() const;
    virtual size_t fields() const;

    virtual bool asBool() const;
    virtual int64_t asLong() const;
    virtual double asDouble() const;
    virtual Memory asString() const;
    virtual Memory asData() const;

    virtual void traverse(ArrayTraverser &at) const;
    virtual void traverse(ObjectSymbolTraverser &ot) const;
    virtual void traverse(ObjectTraverser &ot) const;

    virtual vespalib::string toString() const override;

    virtual Cursor &operator[](size_t idx) const;
    virtual Cursor &operator[](Symbol sym) const;
    virtual Cursor &operator[](Memory name) const;

    virtual Cursor &addNix();
    virtual Cursor &addBool(bool bit);
    virtual Cursor &addLong(int64_t l);
    virtual Cursor &addDouble(double d);
    virtual Cursor &addString(Memory str);
    virtual Cursor &addData(Memory data);
    virtual Cursor &addArray();
    virtual Cursor &addObject();

    virtual Cursor &setNix(Symbol sym);
    virtual Cursor &setBool(Symbol sym, bool bit);
    virtual Cursor &setLong(Symbol sym, int64_t l);
    virtual Cursor &setDouble(Symbol sym, double d);
    virtual Cursor &setString(Symbol sym, Memory str);
    virtual Cursor &setData(Symbol sym, Memory data);
    virtual Cursor &setArray(Symbol sym);
    virtual Cursor &setObject(Symbol sym);

    virtual Cursor &setNix(Memory name);
    virtual Cursor &setBool(Memory name, bool bit);
    virtual Cursor &setLong(Memory name, int64_t l);
    virtual Cursor &setDouble(Memory name, double d);
    virtual Cursor &setString(Memory name, Memory str);
    virtual Cursor &setData(Memory name, Memory str);
    virtual Cursor &setArray(Memory name);
    virtual Cursor &setObject(Memory name);

    virtual Symbol insert(Memory symbol_name);
};

} // namespace vespalib::slime
} // namespace vespalib

