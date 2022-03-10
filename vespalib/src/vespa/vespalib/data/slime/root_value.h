// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "nix_value.h"
#include "value_factory.h"

namespace vespalib::slime {

class RootValue
{
private:
    Value *_value;
    Stash *_stash;

public:
    RootValue(Stash * stash) noexcept : _value(NixValue::instance()), _stash(stash) {}
    RootValue(RootValue && rhs) noexcept : _value(rhs._value), _stash(rhs._stash) {
        rhs._value = NixValue::instance();
        rhs._stash = nullptr;
    }
    RootValue(const RootValue &) = delete;
    RootValue &operator=(const RootValue &) = delete;
    RootValue &operator=(RootValue && rhs) noexcept {
        _value = rhs._value;
        _stash = rhs._stash;
        rhs._value = NixValue::instance();
        rhs._stash = nullptr;
        return *this;
    }
    Cursor &get() const noexcept { return *_value; }
    Cursor &set(const ValueFactory &input) {
        Value *value = input.create(*_stash);
        _value = value;
        return *value;
    }
    Value *wrap(SymbolTable &table, SymbolInserter &symbol);
    ~RootValue() = default;
};

} // namespace vespalib::slime
