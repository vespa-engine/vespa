// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "root_value.h"
#include "object_value.h"
#include <vespa/vespalib/util/stash.h>

namespace vespalib::slime {

Value *
RootValue::wrap(SymbolTable &table, SymbolInserter &symbol) {
    Value *value = & _stash->create<ObjectValue>(table, *_stash, symbol, _value);
    _value = value;
    return _value;
}

} // namespace vespalib::slime
