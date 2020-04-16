// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "symbol_table.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace vespalib::slime {

SymbolTable::SymbolTable(size_t expectedNumSymbols) :
    _symbols(3*expectedNumSymbols),
    _names(expectedNumSymbols, expectedNumSymbols*16)
{ }

SymbolTable::~SymbolTable() = default;

void
SymbolTable::clear() {
    _names.clear();
    _symbols.clear();
}

Symbol
SymbolTable::insert(const Memory &name) {
    SymbolMap::const_iterator pos = _symbols.find(name);
    if (pos == _symbols.end()) {
        Symbol symbol(_names.size());
        SymbolVector::Reference r(_names.push_back(name.data, name.size));
        _symbols.insert(std::make_pair(Memory(r.c_str(), r.size()), symbol));
        return symbol;
    }
    return pos->second;
}
Symbol
SymbolTable::lookup(const Memory &name) const {
    SymbolMap::const_iterator pos = _symbols.find(name);
    if (pos == _symbols.end()) {
        return Symbol();
    }
    return pos->second;
}

}
