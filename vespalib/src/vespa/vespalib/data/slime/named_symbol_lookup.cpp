// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "named_symbol_lookup.h"
#include "symbol_table.h"

namespace vespalib::slime {

Symbol
NamedSymbolLookup::lookup() const {
    return _table.lookup(_name);
}

} // namespace vespalib::slime
