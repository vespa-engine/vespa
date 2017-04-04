// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "symbol_lookup.h"
#include "symbol_inserter.h"

namespace vespalib {
namespace slime {

/**
 * Class containing the pre-resolved symbol for a field. Since the
 * symbol is already known, it is also known to be present in the
 * appropriate symbol table. Thus, this class can satisfy both the
 * symbol lookup and inserter interfaces.
 **/
class ResolvedSymbol : public SymbolLookup,
                       public SymbolInserter
{
private:
    Symbol _symbol;

public:
    ResolvedSymbol(const Symbol &symbol) : _symbol(symbol) {}
    Symbol lookup() const override {
        return _symbol;
    }
    Symbol insert() override {
        return _symbol;
    }
};

} // namespace vespalib::slime
} // namespace vespalib

