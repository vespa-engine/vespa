// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "symbol.h"
#include <vespa/vespalib/data/memory.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/stllike/hash_map.h>

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
    using SymbolVector = std::vector<Memory>;
    Stash        _stash;
    SymbolMap    _symbols;
    SymbolVector _names;

public:
    using UP = std::unique_ptr<SymbolTable>;
    SymbolTable() : SymbolTable(16) {}
    explicit SymbolTable(size_t expectedNumSymbols);
    SymbolTable(SymbolTable &&) noexcept = default;
    SymbolTable & operator=(SymbolTable &&) noexcept = default;
    ~SymbolTable();
    size_t symbols() const noexcept { return _names.size(); }
    Memory inspect(const Symbol &symbol) const {
        if (symbol.getValue() > _names.size()) {
            return Memory();
        }
        return _names[symbol.getValue()];
    }
    Symbol insert(const Memory &name);
    Symbol lookup(const Memory &name) const;
    void clear();
};

}

