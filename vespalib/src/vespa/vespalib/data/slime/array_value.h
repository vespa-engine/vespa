// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"
#include "nix_value.h"
#include "value_factory.h"
#include <vespa/vespalib/stllike/allocator.h>
#include <vector>

namespace vespalib::slime {

/**
 * Class representing a collection of ordered values that can be
 * looked up by index.
 **/
class ArrayValue final : public Value
{
private:
    SymbolTable         &_symbolTable;
    Stash               &_stash;
    std::vector<Value*, vespalib::allocator_large<Value*>>  _values;

protected:
    Cursor &addLeaf(const ValueFactory &input) override {
        Value *value = input.create(_stash);
        _values.push_back(value);
        return *value;
    }

public:
    ArrayValue(SymbolTable &table, Stash & stash);
    ArrayValue(const ArrayValue &) = delete;
    ArrayValue &operator=(const ArrayValue &) = delete;
    void reserve(size_t sz) { _values.reserve(sz); }

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

    Cursor &addArray(size_t reserve) override;
    Cursor &addObject() override;
    Symbol resolve(Memory symbol_name) override;

    ~ArrayValue() override;
};

} // namespace vespalib::slime
