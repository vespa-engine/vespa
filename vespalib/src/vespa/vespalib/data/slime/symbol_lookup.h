// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "symbol.h"

namespace vespalib::slime {

/**
 * Interface used to look up the symbol for a field.
 **/
struct SymbolLookup {
    virtual Symbol lookup() const = 0;
    virtual ~SymbolLookup() = default;
};

} // namespace vespalib::slime
