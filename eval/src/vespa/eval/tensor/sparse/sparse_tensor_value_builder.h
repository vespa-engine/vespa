// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor_value.h"
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
    SparseTensorValueIndex _index;
    std::vector<T> _cells;
    Stash _stash;
    SparseTensorAddressBuilder _addr_builder;
public:
    SparseTensorValueBuilder(const eval::ValueType &type,
                             size_t num_mapped_in,
                             size_t subspace_size_in,
                             size_t expected_subspaces)
      : _type(type),
        _index(),
        _cells(),
        _stash()
    {
        assert(num_mapped_in > 0);
        assert(subspace_size_in == 1);
        _cells.reserve(expected_subspaces);
    }

    ~SparseTensorValueBuilder() override = default;

    ArrayRef<T> add_subspace(const std::vector<vespalib::stringref> &addr) override;
    std::unique_ptr<eval::Value> build(std::unique_ptr<eval::ValueBuilder<T>> self) override;
};

} // namespace
