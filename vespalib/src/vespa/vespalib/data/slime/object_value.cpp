// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "object_value.h"
#include "object_traverser.h"
#include "empty_value_factory.h"
#include "resolved_symbol.h"
#include "named_symbol_lookup.h"
#include "named_symbol_inserter.h"
#include "symbol_table.h"

namespace vespalib::slime {

Cursor &
ObjectValue::setLeaf(Symbol sym, const ValueFactory &input) {
    ResolvedSymbol symbol(sym);
    return setIfUnset(symbol, input);

}

Cursor &
ObjectValue::setLeaf(Memory name, const ValueFactory &input) {
    NamedSymbolInserter symbol(_symbolTable, name);
    return setIfUnset(symbol, input);
}

void
ObjectValue::traverse(ObjectSymbolTraverser &ot) const {
    for (const auto & field : _fields) {
        ot.field(field.first, *field.second);
    }
}

void
ObjectValue::traverse(ObjectTraverser &ot) const {
    for (const auto & field : _fields) {
        Memory symbol = _symbolTable.inspect(field.first);
        ot.field(symbol, *field.second);
    }
}

Cursor &
ObjectValue::operator[](Symbol sym) const {
    ResolvedSymbol symbol(sym);
    return *lookup(symbol);
}

Cursor &
ObjectValue::operator[](Memory name) const {
    NamedSymbolLookup symbol(_symbolTable, name);
    return *lookup(symbol);
}


Cursor &
ObjectValue::setArray(Symbol symbol, size_t reserve) {
    return setLeaf(symbol, ArrayValueFactory(_symbolTable, reserve));
}

Cursor &
ObjectValue::setObject(Symbol symbol) {
    return setLeaf(symbol, ObjectValueFactory(_symbolTable));
}

Cursor &
ObjectValue::setArray(Memory name, size_t reserve) {
    return setLeaf(name, ArrayValueFactory(_symbolTable, reserve));
}

Cursor &
ObjectValue::setObject(Memory name) {
    return setLeaf(name, ObjectValueFactory(_symbolTable));
}

Symbol
ObjectValue::resolve(Memory symbol_name) { return _symbolTable.insert(symbol_name); }

} // namespace vespalib::slime
