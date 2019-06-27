// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor.h"

namespace vespalib::tensor {

extern size_t num_typed_tensor_builder_inserts;

/**
 * Class for building a dense tensor by inserting cell values directly into underlying array of cells.
 */
template <typename CT>
class TypedDenseTensorBuilder
{
public:
    using Address = DenseTensorView::Address;
private:
    eval::ValueType _type;
    std::vector<CT> _cells;

    static size_t calculateCellAddress(const Address &address, const eval::ValueType &type) {
        size_t result = 0;
        for (size_t i = 0; i < address.size(); ++i) {
            result *= type.dimensions()[i].size;
            result += address[i];
        }
        return result;
    }
public:
    TypedDenseTensorBuilder(const eval::ValueType &type_in);
    ~TypedDenseTensorBuilder();
    void insertCell(const Address &address, CT cellValue) {
        insertCell(calculateCellAddress(address, _type), cellValue);
    }
    void insertCell(size_t index, CT cellValue) {
        _cells[index] = cellValue;
        ++num_typed_tensor_builder_inserts;
    }
    Tensor::UP build();
};

}
