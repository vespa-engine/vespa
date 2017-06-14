// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value_factory.h"
#include "basic_value.h"
#include <vespa/vespalib/util/stash.h>

namespace vespalib {
namespace slime {

struct BoolValueFactory : public ValueFactory {
    bool input;
    BoolValueFactory(bool in) : input(in) {}
    Value *create(Stash & stash) const override { return & stash.create<BasicBoolValue>(input); }
};

struct LongValueFactory : public ValueFactory {
    int64_t input;
    LongValueFactory(int64_t in) : input(in) {}
    Value *create(Stash & stash) const override { return & stash.create<BasicLongValue>(input); }
};

struct DoubleValueFactory : public ValueFactory {
    double input;
    DoubleValueFactory(double in) : input(in) {}
    Value *create(Stash & stash) const override { return & stash.create<BasicDoubleValue>(input); }
};

struct StringValueFactory : public ValueFactory {
    Memory input;
    StringValueFactory(Memory in) : input(in) {}
    Value *create(Stash & stash) const override { return & stash.create<BasicStringValue>(input, stash); }
};

struct DataValueFactory : public ValueFactory {
    Memory input;
    DataValueFactory(Memory in) : input(in) {}
    Value *create(Stash & stash) const override { return & stash.create<BasicDataValue>(input, stash); }
};

} // namespace vespalib::slime
} // namespace vespalib

