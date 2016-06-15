// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "compact_tensor.h"
#include "compact_tensor_address_builder.h"
#include "compact_tensor_unsorted_address_builder.h"
#include <vespa/vespalib/tensor/tensor_builder.h>
#include <vespa/vespalib/tensor/tensor_address.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/util/stash.h>

namespace vespalib {
namespace tensor {

/**
 * A builder of compact tensors.
 */
class CompactTensorBuilder : public TensorBuilder
{
    CompactTensorUnsortedAddressBuilder _addressBuilder; // unsorted dimensions
    CompactTensorAddressBuilder _normalizedAddressBuilder; // sorted dimensions
    CompactTensor::Cells _cells;
    Stash _stash;
    vespalib::hash_map<vespalib::string, uint32_t> _dimensionsEnum;
    std::vector<vespalib::string> _dimensions;
public:
    CompactTensorBuilder();
    virtual ~CompactTensorBuilder();

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
