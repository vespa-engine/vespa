// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"

namespace vespalib::eval {

/**
 * A trivial builder for DoubleValue objects
 **/
class DoubleValueBuilder : public ValueBuilder<double>
{
private:
    double _value;
public:
    DoubleValueBuilder(const ValueType &type, size_t num_mapped_in,
                       size_t subspace_size_in, size_t expected_subspaces);
    ~DoubleValueBuilder() override;
    ArrayRef<double>
    add_subspace(const std::vector<vespalib::stringref> &) override {
        return ArrayRef<double>(&_value, 1);
    }
    std::unique_ptr<Value>
    build(std::unique_ptr<ValueBuilder<double>>) override {
        return std::make_unique<DoubleValue>(_value);
    }
};

} // namespace
