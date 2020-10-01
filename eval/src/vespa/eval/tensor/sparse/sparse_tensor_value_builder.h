// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor.h"
#include "sparse_tensor_address_builder.h"

namespace vespalib::tensor {

/**
 * A builder for SparseTensorValue objects
 * appropriate for cell type T.
 **/
template <typename T>
class SparseTensorValueBuilder : public eval::ValueBuilder<T>
{
private:
    eval::ValueType _type;
    SparseTensorIndex _index;
    std::vector<T> _cells;
    SparseTensorAddressBuilder _addr_builder;
public:
    SparseTensorValueBuilder(const eval::ValueType &type,
                             size_t num_mapped_in,
                             size_t expected_subspaces)
      : _type(type),
        _index(num_mapped_in),
        _cells()
    {
        assert(num_mapped_in > 0);
        _index.reserve(expected_subspaces);
        _cells.reserve(expected_subspaces);
    }

    ~SparseTensorValueBuilder() override = default;

    ArrayRef<T> add_subspace(ConstArrayRef<vespalib::stringref> addr) override;
    std::unique_ptr<eval::Value> build(std::unique_ptr<eval::ValueBuilder<T>> self) override;
};

} // namespace
