// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "type.h"
#include "cursor.h"

namespace vespalib::slime {

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
    virtual ~Value() = default;

    virtual Cursor &addLeaf(const ValueFactory &input);
    virtual Cursor &setLeaf(Symbol symbol, const ValueFactory &input);
    virtual Cursor &setLeaf(Memory name, const ValueFactory &input);

public:
    bool valid() const override;
    Type type() const override;
    size_t children() const override;
    size_t entries() const override;
    size_t fields() const override;

    bool asBool() const override;
    int64_t asLong() const override;
    double asDouble() const override;
    Memory asString() const override;
    Memory asData() const override;

    void traverse(ArrayTraverser &at) const override;
    void traverse(ObjectSymbolTraverser &ot) const override;
    void traverse(ObjectTraverser &ot) const override;

    vespalib::string toString() const override;

    Cursor &operator[](size_t idx) const override;
    Cursor &operator[](Symbol sym) const override;
    Cursor &operator[](Memory name) const override;

    Cursor &addNix() override;
    Cursor &addBool(bool bit) override;
    Cursor &addLong(int64_t l) override;
    Cursor &addDouble(double d) override;
    Cursor &addString(Memory str) override;
    Cursor &addData(Memory data) override;
    Cursor &addData(ExternalMemory::UP data) override;
    Cursor &addArray(size_t reserved_size) override;
    Cursor &addObject() override;

    Cursor &setNix(Symbol sym) override;
    Cursor &setBool(Symbol sym, bool bit) override;
    Cursor &setLong(Symbol sym, int64_t l) override;
    Cursor &setDouble(Symbol sym, double d) override;
    Cursor &setString(Symbol sym, Memory str) override;
    Cursor &setData(Symbol sym, Memory data) override;
    Cursor &setData(Symbol sym, ExternalMemory::UP data) override;
    Cursor &setArray(Symbol sym, size_t reserved_size) override;
    Cursor &setObject(Symbol sym) override;

    Cursor &setNix(Memory name) override;
    Cursor &setBool(Memory name, bool bit) override;
    Cursor &setLong(Memory name, int64_t l) override;
    Cursor &setDouble(Memory name, double d) override;
    Cursor &setString(Memory name, Memory str) override;
    Cursor &setData(Memory name, Memory str) override;
    Cursor &setData(Memory name, ExternalMemory::UP data) override;
    Cursor &setArray(Memory name, size_t reserved_size) override;
    Cursor &setObject(Memory name) override;

    Symbol resolve(Memory symbol_name) override;
};

} // namespace vespalib::slime
