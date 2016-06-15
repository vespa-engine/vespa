// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "symbol.h"
#include "memory.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/data/memorydatastore.h>

namespace vespalib {
namespace slime {

/**
 * Maps between strings and symbols.
 **/
class SymbolTable
{
private:
    struct hasher {
        size_t operator () (const Memory & lcm) const {
            return lcm.hash();
        }
    };
    typedef hash_map<Memory, Symbol, hasher> SymbolMap;
    typedef VariableSizeVector SymbolVector;
    SymbolMap    _symbols;
    SymbolVector _names;

public:
    typedef std::unique_ptr<SymbolTable> UP;
    SymbolTable(size_t expectedNumSymbols=16) : _symbols(3*expectedNumSymbols), _names() {}
    size_t symbols() const { return _names.size(); }
    Memory inspect(const Symbol &symbol) const {
        if (symbol.getValue() > _names.size()) {
            return Memory();
        }
        SymbolVector::Reference r(_names[symbol.getValue()]);
        return Memory(r.c_str(), r.size());
    }
    Symbol insert(const Memory &name) {
        SymbolMap::const_iterator pos = _symbols.find(name);
        if (pos == _symbols.end()) {
            Symbol symbol(_names.size());
            SymbolVector::Reference r(_names.push_back(name.data, name.size));
            _symbols.insert(std::make_pair(Memory(r.c_str(), r.size()), symbol));
            return symbol;
        }
        return pos->second;
    }
    Symbol lookup(const Memory &name) const {
        SymbolMap::const_iterator pos = _symbols.find(name);
        if (pos == _symbols.end()) {
            return Symbol();
        }
        return pos->second;
    }
    void clear() {
        _names.clear();
        _symbols.clear();
    }
};

} // namespace vespalib::slime
} // namespace vespalib

