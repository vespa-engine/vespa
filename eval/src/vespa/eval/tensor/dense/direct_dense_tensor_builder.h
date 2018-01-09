// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor.h"

namespace vespalib::tensor {

/**
 * Class for building a dense tensor by inserting cell values directly into underlying array of cells.
 */
class DirectDenseTensorBuilder
{
public:
    using Cells = DenseTensor::Cells;
    using Address = DenseTensor::Address;

private:
    eval::ValueType _type;
    Cells _cells;

    static size_t calculateCellAddress(const Address &address, const eval::ValueType &type) {
        size_t result = 0;
        for (size_t i = 0; i < address.size(); ++i) {
            result *= type.dimensions()[i].size;
            result += address[i];
        }
        return result;
    }
public:
    DirectDenseTensorBuilder(const eval::ValueType &type_in);
    ~DirectDenseTensorBuilder();
    void insertCell(const Address &address, double cellValue) {
        _cells[calculateCellAddress(address, _type)] = cellValue;
    }
    Tensor::UP build();
};

}

