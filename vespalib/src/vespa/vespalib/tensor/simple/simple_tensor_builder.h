// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "simple_tensor.h"
#include <vespa/vespalib/tensor/tensor_builder.h>
#include <vespa/vespalib/tensor/tensor_address.h>
#include <vespa/vespalib/tensor/tensor_address_builder.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace vespalib {
namespace tensor {

/**
 * A simple builder of tensors (sparse multi-dimensional array).
 *
 * A sparse tensor is a set of cells containing scalar values.
 * Each cell is identified by its address, which consists of a set of dimension -> label pairs,
 * where both dimension and label is a string on the form of an identifier or integer.
 */
class SimpleTensorBuilder : public TensorBuilder
{
    TensorAddressBuilder _addressBuilder;
    SimpleTensor::Cells _cells;
    vespalib::hash_map<vespalib::string, uint32_t> _dimensionsEnum;
    std::vector<vespalib::string> _dimensions;
public:
    SimpleTensorBuilder();
    virtual ~SimpleTensorBuilder();

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
