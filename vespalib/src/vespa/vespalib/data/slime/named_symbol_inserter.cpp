// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "named_symbol_inserter.h"
#include "symbol_table.h"

namespace vespalib::slime {

Symbol
NamedSymbolInserter::insert() {
    return _table.insert(_name);
}
} // namespace vespalib::slime
