// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "symbol_lookup.h"
#include "symbol_table.h"

namespace vespalib::slime {

/**
 * Class used to look up the name of a field in a symbol table.
 **/
class NamedSymbolLookup : public SymbolLookup
{
private:
    const SymbolTable &_table;
    const Memory &_name;

public:
    NamedSymbolLookup(const SymbolTable &table, const Memory &name)
        : _table(table), _name(name) {}
    Symbol lookup() const override {
        return _table.lookup(_name);
    }
};

} // namespace vespalib::slime
