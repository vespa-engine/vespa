// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value_factory.h"
#include "nix_value.h"
#include "array_value.h"
#include "object_value.h"
#include <vespa/vespalib/util/stash.h>

namespace vespalib {
namespace slime {

struct NixValueFactory : public ValueFactory {
    Value *create(Stash &) const override { return NixValue::instance(); }
};

struct ArrayValueFactory : public ValueFactory {
    SymbolTable &symbolTable;
    ArrayValueFactory(SymbolTable &table) : symbolTable(table) {}
    Value *create(Stash & stash) const override;
};

struct ObjectValueFactory : public ValueFactory {
    SymbolTable &symbolTable;
    ObjectValueFactory(SymbolTable &table) : symbolTable(table) {}
    Value *create(Stash & stash) const override;
};

} // namespace vespalib::slime
} // namespace vespalib

