// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "empty_value_factory.h"

namespace vespalib {
namespace slime {

Value *
ArrayValueFactory::create(Stash & stash) const {
    return & stash.create<ArrayValue>(symbolTable, stash);
}

Value *
ObjectValueFactory::create(Stash & stash) const {
    return & stash.create<ObjectValue>(symbolTable, stash);
}

} // namespace vespalib::slime
} // namespace vespalib
