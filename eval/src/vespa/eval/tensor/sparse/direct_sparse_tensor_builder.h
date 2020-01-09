// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/hdr_abort.h>
#include "sparse_tensor.h"
#include "sparse_tensor_address_builder.h"

namespace vespalib::tensor {

/**
 * Utility class to build tensors of type SparseTensor, to be used by
 * tensor operations.
 */
class DirectSparseTensorBuilder
{
public:
    using Cells = SparseTensor::Cells;
    using AddressBuilderType = SparseTensorAddressBuilder;
    using AddressRefType = SparseTensorAddressRef;

private:
    Stash _stash;
    eval::ValueType _type;
    Cells _cells;

public:
    void
    copyCells(const Cells &cells_in)
    {
        for (const auto &cell : cells_in) {
            SparseTensorAddressRef oldRef = cell.first;
            SparseTensorAddressRef newRef(oldRef, _stash);
            _cells[newRef] = cell.second;
        }
    }

    DirectSparseTensorBuilder()
        : _stash(SparseTensor::STASH_CHUNK_SIZE),
          _type(eval::ValueType::double_type()),
          _cells()
    {
    }

    DirectSparseTensorBuilder(const eval::ValueType &type_in)
        : _stash(SparseTensor::STASH_CHUNK_SIZE),
          _type(type_in),
          _cells()
    {
    }

    DirectSparseTensorBuilder(const eval::ValueType &type_in, const Cells &cells_in)
        : _stash(SparseTensor::STASH_CHUNK_SIZE),
          _type(type_in),
          _cells()
    {
        copyCells(cells_in);
    }

    ~DirectSparseTensorBuilder() {};

    Tensor::UP build() {
        return std::make_unique<SparseTensor>(std::move(_type), std::move(_cells), std::move(_stash));
    }

    template <class Function>
    void insertCell(SparseTensorAddressRef address, double value, Function &&func)
    {
        auto res = _cells.insert(std::make_pair(address, value));
        if (res.second) {
            // Replace key with own copy
            res.first->first = SparseTensorAddressRef(address, _stash);
        } else {
            res.first->second = func(res.first->second, value);
        }
    }

    void insertCell(SparseTensorAddressRef address, double value) {
        // This address should not already exist and a new cell should be inserted.
        insertCell(address, value, [](double, double) -> double { HDR_ABORT("should not be reached"); });
    }

    template <class Function>
    void insertCell(SparseTensorAddressBuilder &address, double value, Function &&func)
    {
        insertCell(address.getAddressRef(), value, func);
    }

    void insertCell(SparseTensorAddressBuilder &address, double value) {
        // This address should not already exist and a new cell should be inserted.
        insertCell(address.getAddressRef(), value, [](double, double) -> double { HDR_ABORT("should not be reached"); });
    }

    eval::ValueType &fast_type() { return _type; }
    Cells &cells() { return _cells; }
    void reserve(uint32_t estimatedCells) { _cells.resize(estimatedCells*2); }
};

}
