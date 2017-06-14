// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"
#include "nix_value.h"
#include "value_factory.h"
#include "symbol_table.h"
#include <vector>
#include <vespa/vespalib/util/stash.h>

namespace vespalib {
namespace slime {

/**
 * Class representing a collection of ordered values that can be
 * looked up by index.
 **/
class ArrayValue : public Value
{
private:
    SymbolTable         &_symbolTable;
    Stash               &_stash;
    std::vector<Value*>  _values;

protected:
    Cursor &addLeaf(const ValueFactory &input) override {
        Value *value = input.create(_stash);
        _values.push_back(value);
        return *value;
    }

public:
    ArrayValue(SymbolTable &table, Stash & stash) : _symbolTable(table), _stash(stash), _values() {}
    ArrayValue(const ArrayValue &) = delete;
    ArrayValue &operator=(const ArrayValue &) = delete;

    Type type() const override { return ARRAY::instance; }
    size_t children() const override { return _values.size(); }
    size_t entries() const override { return _values.size(); }
    void traverse(ArrayTraverser &at) const override;

    Cursor &operator[](size_t idx) const override {
        if (idx < _values.size()) {
            return *_values[idx];
        }
        return *NixValue::invalid();
    }

    Cursor &addArray() override;
    Cursor &addObject() override;
    Symbol resolve(Memory symbol_name) override;

    ~ArrayValue() { }
};

} // namespace vespalib::slime
} // namespace vespalib

