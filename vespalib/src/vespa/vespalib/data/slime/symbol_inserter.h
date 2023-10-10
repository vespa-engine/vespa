// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "symbol.h"

namespace vespalib::slime {

/**
 * Interface used to obtain the symbol for a field, and insert it into
 * the symbol table if needed.
 **/
struct SymbolInserter {
    virtual Symbol insert() = 0;
    virtual ~SymbolInserter() {}
};

} // namespace vespalib::slime

