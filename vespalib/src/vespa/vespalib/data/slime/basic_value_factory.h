// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value_factory.h"
#include <vespa/vespalib/data/memory.h>

namespace vespalib::slime {

struct BoolValueFactory final : public ValueFactory {
    bool input;
    BoolValueFactory(bool in) noexcept : input(in) {}
    Value *create(Stash & stash) const override;
};

struct LongValueFactory final : public ValueFactory {
    int64_t input;
    LongValueFactory(int64_t in) noexcept : input(in) {}
    Value *create(Stash & stash) const override;
};

struct DoubleValueFactory final : public ValueFactory {
    double input;
    DoubleValueFactory(double in) noexcept : input(in) {}
    Value *create(Stash & stash) const override;
};

struct StringValueFactory final : public ValueFactory {
    Memory input;
    StringValueFactory(Memory in) noexcept : input(in) {}
    Value *create(Stash & stash) const override;
};

struct DataValueFactory final : public ValueFactory {
    Memory input;
    DataValueFactory(Memory in) noexcept : input(in) {}
    Value *create(Stash & stash) const override;
};

} // namespace vespalib::slime
