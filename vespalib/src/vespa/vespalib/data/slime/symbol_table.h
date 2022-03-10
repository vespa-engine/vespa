// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "symbol.h"
#include <vespa/vespalib/data/memory.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/data/memorydatastore.h>

namespace vespalib::slime {

/**
 * Maps between strings and symbols.
 **/
class SymbolTable
{
private:
    struct hasher {
        size_t operator () (const Memory & lcm) const {
            return hashValue(lcm.data, lcm.size);
        }
    };
    using SymbolMap = hash_map<Memory, Symbol, hasher>;
    using SymbolVector = VariableSizeVector;
    SymbolMap    _symbols;
    SymbolVector _names;

public:
    typedef std::unique_ptr<SymbolTable> UP;
    SymbolTable(size_t expectedNumSymbols=16);
    ~SymbolTable();
    size_t symbols() const noexcept { return _names.size(); }
    Memory inspect(const Symbol &symbol) const {
        if (symbol.getValue() > _names.size()) {
            return Memory();
        }
        SymbolVector::Reference r(_names[symbol.getValue()]);
        return Memory(r.c_str(), r.size());
    }
    Symbol insert(const Memory &name);
    Symbol lookup(const Memory &name) const;
    void clear();
};

}

