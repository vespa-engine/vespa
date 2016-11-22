// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "symbol_table.h"

namespace vespalib {
namespace slime {

SymbolTable::SymbolTable(size_t expectedNumSymbols) :
    _symbols(3*expectedNumSymbols),
    _names()
{ }

SymbolTable::~SymbolTable() { }

void
SymbolTable::clear() {
    _names.clear();
    _symbols.clear();
}

} // namespace vespalib::slime
} // namespace vespalib
