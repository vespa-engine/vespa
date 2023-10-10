// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_value.h"
#include "array_traverser.h"
#include "empty_value_factory.h"
#include "symbol_table.h"

namespace vespalib::slime {

ArrayValue::ArrayValue(SymbolTable &table, Stash & stash)
    : _symbolTable(table),
      _stash(stash),
      _values()
{}

ArrayValue::~ArrayValue() = default;

void
ArrayValue::traverse(ArrayTraverser &at) const {
    for (size_t i = 0; i < _values.size(); ++i) {
        at.entry(i, *_values[i]);
    }
}


Cursor &
ArrayValue::addArray(size_t reserve) {
    return addLeaf(ArrayValueFactory(_symbolTable, reserve));
}

Cursor &
ArrayValue::addObject() {
    return addLeaf(ObjectValueFactory(_symbolTable));
}

Symbol
ArrayValue::resolve(Memory symbol_name) { return _symbolTable.insert(symbol_name); }

} // namespace vespalib::slime
