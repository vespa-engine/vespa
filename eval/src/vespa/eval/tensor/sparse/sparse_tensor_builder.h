// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor.h"
#include "sparse_tensor_address_builder.h"
#include "sparse_tensor_unsorted_address_builder.h"
#include <vespa/eval/tensor/tensor_address.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/util/stash.h>

namespace vespalib::tensor {

/**
 * A builder of sparse tensors.
 */
class SparseTensorBuilder
{
public:
    using Dimension = uint32_t;
private:
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
    ~SparseTensorBuilder();

    Dimension define_dimension(const vespalib::string &dimension);
    SparseTensorBuilder & add_label(Dimension dimension, const vespalib::string &label);
    SparseTensorBuilder &add_cell(double value);
    Tensor::UP build();
};

}

