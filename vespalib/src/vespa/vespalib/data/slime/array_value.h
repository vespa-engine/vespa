// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    virtual Cursor &addLeaf(const ValueFactory &input) {
        Value *value = input.create(_stash);
        _values.push_back(value);
        return *value;
    }

public:
    ArrayValue(SymbolTable &table, Stash & stash) : _symbolTable(table), _stash(stash), _values() {}
    ArrayValue(const ArrayValue &) = delete;
    ArrayValue &operator=(const ArrayValue &) = delete;

    virtual Type type() const { return ARRAY::instance; }
    virtual size_t children() const { return _values.size(); }
    virtual size_t entries() const { return _values.size(); }
    virtual void traverse(ArrayTraverser &at) const;

    virtual Cursor &operator[](size_t idx) const {
        if (idx < _values.size()) {
            return *_values[idx];
        }
        return *NixValue::invalid();
    }

    virtual Cursor &addArray();
    virtual Cursor &addObject();
    virtual Symbol insert(Memory symbol_name);

    virtual ~ArrayValue() { }
};

} // namespace vespalib::slime
} // namespace vespalib

