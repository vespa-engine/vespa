// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor.h"

namespace vespalib::tensor {

/**
 * A builder for DenseTensor objects
 **/
template<typename T>
class DenseTensorValueBuilder : public eval::ValueBuilder<T>
{
private:
    eval::ValueType _type;
    std::vector<T> _cells;
public:
    DenseTensorValueBuilder(const eval::ValueType &type, size_t subspace_size_in);
    ~DenseTensorValueBuilder() override;
    ArrayRef<T>
    add_subspace(const std::vector<vespalib::stringref> &) override {
        return _cells;
    }
    std::unique_ptr<eval::Value>
    build(std::unique_ptr<eval::ValueBuilder<T>>) override {
        return std::make_unique<DenseTensor<T>>(std::move(_type), std::move(_cells));
    }
};

} // namespace
