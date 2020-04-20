// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

namespace vespalib::slime {

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
        if (pos != nullptr) {
            return *NixValue::invalid();
        }
        pos = input.create(_stash);
        return *pos;
    }

    Value *lookup(const SymbolLookup &symbol) const {
        auto pos = _fields.find(symbol.lookup());
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


    Type type() const override { return OBJECT::instance; }
    size_t children() const override { return _fields.size(); }
    size_t fields() const override { return _fields.size(); }
    void traverse(ObjectSymbolTraverser &ot) const override;
    void traverse(ObjectTraverser &ot) const override;

    Cursor &operator[](Symbol sym) const override;
    Cursor &operator[](Memory name) const override;

    Cursor &setArray(Symbol sym) override;
    Cursor &setObject(Symbol sym) override;

    Cursor &setArray(Memory name) override;
    Cursor &setObject(Memory name) override;
    Symbol resolve(Memory symbol_name) override;

    ~ObjectValue() override = default;
};

}
