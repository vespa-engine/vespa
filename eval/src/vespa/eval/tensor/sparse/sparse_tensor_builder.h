// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor.h"
#include "sparse_tensor_address_builder.h"
#include "sparse_tensor_unsorted_address_builder.h"
#include <vespa/eval/tensor/tensor_builder.h>
#include <vespa/eval/tensor/tensor_address.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/util/stash.h>

namespace vespalib {
namespace tensor {

/**
 * A builder of sparse tensors.
 */
class SparseTensorBuilder : public TensorBuilder
{
    SparseTensorUnsortedAddressBuilder _addressBuilder; // unsorted dimensions
    SparseTensorAddressBuilder _normalizedAddressBuilder; // sorted dimensions
    SparseTensor::Cells _cells;
    Stash _stash;
    vespalib::hash_map<vespalib::string, uint32_t> _dimensionsEnum;
    std::vector<vespalib::string> _dimensions;
    eval::ValueType _type;
    bool _type_made;

    void makeType();
public:
    SparseTensorBuilder();
    virtual ~SparseTensorBuilder();

    virtual Dimension
    define_dimension(const vespalib::string &dimension) override;
    virtual TensorBuilder &
    add_label(Dimension dimension,
              const vespalib::string &label) override;
    virtual TensorBuilder &add_cell(double value) override;

    virtual Tensor::UP build() override;
};

} // namespace vespalib::tensor
} // namespace vespalib
