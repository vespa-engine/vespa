// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "symbol_inserter.h"

namespace vespalib { struct Memory; }

namespace vespalib::slime {

class SymbolTable;

/**
 * Class used to insert the name of a field into a symbol table.
 **/
class NamedSymbolInserter final : public SymbolInserter
{
private:
    SymbolTable &_table;
    const Memory &_name;

public:
    NamedSymbolInserter(SymbolTable &table, const Memory &name) noexcept
        : _table(table), _name(name) {}
    Symbol insert() override;
};

} // namespace vespalib::slime

