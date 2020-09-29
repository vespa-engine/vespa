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
    using AddressBuilderType = SparseTensorAddressBuilder;
    using AddressRefType = SparseTensorAddressRef;

private:
    eval::ValueType _type;
    SparseTensorIndex _index;
    std::vector<double> _values;

public:
    DirectSparseTensorBuilder();
    DirectSparseTensorBuilder(const eval::ValueType &type_in);
    ~DirectSparseTensorBuilder();

    Tensor::UP build();

    template <class Function>
    void insertCell(SparseTensorAddressRef address, double value, Function &&func)
    {
        size_t idx;
        if (_index.lookup_address(address, idx)) {
            _values[idx] = func(_values[idx], value);
        } else {
            idx = _index.lookup_or_add(address);
            assert (idx == _values.size());
            _values.push_back(value);
        }
    }

    void insertCell(SparseTensorAddressRef address, double value) {
        // This address should not already exist and a new cell should be inserted.
        size_t idx = _index.lookup_or_add(address);
        assert (idx == _values.size());
        _values.push_back(value);
    }

    template <class Function>
    void insertCell(SparseTensorAddressBuilder &address, double value, Function &&func) {
        insertCell(address.getAddressRef(), value, func);
    }

    void insertCell(SparseTensorAddressBuilder &address, double value) {
        // This address should not already exist and a new cell should be inserted.
        insertCell(address.getAddressRef(), value);
    }

    eval::ValueType &fast_type() { return _type; }

    void reserve(uint32_t estimatedCells);
};

}
