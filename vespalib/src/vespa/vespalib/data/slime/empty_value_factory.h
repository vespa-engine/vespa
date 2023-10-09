// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value_factory.h"
#include "nix_value.h"

namespace vespalib::slime {

struct NixValueFactory final : public ValueFactory {
    Value *create(Stash &) const override { return NixValue::instance(); }
};

struct ArrayValueFactory final : public ValueFactory {
    SymbolTable &symbolTable;
    size_t _reserve;
    ArrayValueFactory(SymbolTable &table, size_t reserve) noexcept : symbolTable(table), _reserve(reserve) {}
    Value *create(Stash & stash) const override;
};

struct ObjectValueFactory final : public ValueFactory {
    SymbolTable &symbolTable;
    ObjectValueFactory(SymbolTable &table) noexcept : symbolTable(table) {}
    Value *create(Stash & stash) const override;
};

} // namespace vespalib::slime
