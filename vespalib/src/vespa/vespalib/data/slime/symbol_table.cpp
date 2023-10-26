// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "symbol_table.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace vespalib::slime {

SymbolTable::SymbolTable(size_t expectedNumSymbols)
    : _stash(),
      _symbols(3*expectedNumSymbols),
      _names()
{
    _names.reserve(expectedNumSymbols);
}

SymbolTable::~SymbolTable() = default;

void
SymbolTable::clear() {
    _names.clear();
    _symbols.clear();
    _stash.clear();
}

Symbol
SymbolTable::insert(const Memory &name) {
    SymbolMap::const_iterator pos = _symbols.find(name);
    if (pos == _symbols.end()) {
        Symbol symbol(_names.size());
        char *buf = _stash.alloc(name.size);
        memcpy(buf, name.data, name.size);
        Memory backed(buf, name.size);
        _names.push_back(backed);
        _symbols.insert(std::make_pair(backed, symbol));
        return symbol;
    }
    return pos->second;
}

Symbol
SymbolTable::lookup(const Memory &name) const {
    SymbolMap::const_iterator pos = _symbols.find(name);
    if (pos == _symbols.end()) {
        return {};
    }
    return pos->second;
}

}
