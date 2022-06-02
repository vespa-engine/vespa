// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value_builder_factory.h"

namespace vespalib::eval {

/**
 * A trivial builder for DoubleValue objects
 **/
class DoubleValueBuilder : public ValueBuilder<double>
{
private:
    double _value;
public:
    DoubleValueBuilder() : _value(0.0) {}
    ~DoubleValueBuilder() override;
    ArrayRef<double>
    add_subspace(ConstArrayRef<vespalib::stringref>) override {
        return ArrayRef<double>(&_value, 1);
    }
    std::unique_ptr<Value>
    build(std::unique_ptr<ValueBuilder<double>>) override {
        return std::make_unique<DoubleValue>(_value);
    }
};

} // namespace
