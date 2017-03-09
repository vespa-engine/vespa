// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor.h"

namespace vespalib {
namespace tensor {

/**
 * Class for building a dense tensor by inserting cell values directly into underlying array of cells.
 */
class DirectDenseTensorBuilder
{
public:
    using Cells = DenseTensor::Cells;
    using Address = std::vector<size_t>;

private:
    eval::ValueType _type;
    Cells _cells;

public:
    DirectDenseTensorBuilder(const eval::ValueType &type_in);
    ~DirectDenseTensorBuilder();
    void insertCell(const Address &address, double cellValue);
    Tensor::UP build();
};

} // namespace tensor
} // namespace vespalib
