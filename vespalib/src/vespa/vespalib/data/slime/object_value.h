// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"
#include "nix_value.h"
#include "symbol.h"
#include "symbol_lookup.h"
#include "symbol_table.h"
#include "value_factory.h"
#include "symbol_inserter.h"
#include <vespa/vespalib/stllike/vector_map.h>
#include <vespa/vespalib/util/stash.h>

namespace vespalib {
namespace slime {

/**
 * Class representing a collection of unordered values that can be
 * looked up by symbol.
 **/
class ObjectValue : public Value
{
private:
    struct hasher {
        size_t operator () (const Symbol & s) const { return s.getValue(); }
    };
    typedef vector_map<Symbol, Value*> SymbolValueMap;
    SymbolTable    &_symbolTable;
    Stash          &_stash;
    SymbolValueMap  _fields;

    Cursor &setIfUnset(SymbolInserter &symbol, const ValueFactory &input) {
        Value *&pos = _fields[symbol.insert()];
        if (pos != 0) {
            return *NixValue::invalid();
        }
        pos = input.create(_stash);
        return *pos;
    }

    Value *lookup(const SymbolLookup &symbol) const {
        SymbolValueMap::const_iterator pos = _fields.find(symbol.lookup());
        if (pos == _fields.end()) {
            return NixValue::invalid();
        }
        return pos->second;
    }
protected:
    Cursor &setLeaf(Symbol sym, const ValueFactory &input) override;
    Cursor &setLeaf(Memory name, const ValueFactory &input) override;

public:
    ObjectValue(SymbolTable &table, Stash & stash) : _symbolTable(table), _stash(stash), _fields() {
        _fields.reserve(4);
    }

    ObjectValue(SymbolTable &table, Stash & stash, SymbolInserter &symbol, Value *value)
        : _symbolTable(table), _stash(stash), _fields()
    {
        _fields.reserve(4);
        _fields[symbol.insert()] = value;
    }
    ObjectValue(const ObjectValue &) = delete;
    ObjectValue &operator=(const ObjectValue &) = delete;


    virtual Type type() const { return OBJECT::instance; }
    virtual size_t children() const { return _fields.size(); }
    virtual size_t fields() const { return _fields.size(); }
    virtual void traverse(ObjectSymbolTraverser &ot) const;
    virtual void traverse(ObjectTraverser &ot) const;

    virtual Cursor &operator[](Symbol sym) const;
    virtual Cursor &operator[](Memory name) const;

    virtual Cursor &setArray(Symbol sym);
    virtual Cursor &setObject(Symbol sym);

    virtual Cursor &setArray(Memory name);
    virtual Cursor &setObject(Memory name);
    virtual Symbol resolve(Memory symbol_name);

    virtual ~ObjectValue() { }
};

} // namespace vespalib::slime
} // namespace vespalib

