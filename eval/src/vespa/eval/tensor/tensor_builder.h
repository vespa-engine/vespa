// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace tensor {

class Tensor;

/**
 * An interfrace for builder of tensors (sparse multi-dimensional array).
 *
 * A sparse tensor is a set of cells containing scalar values.  Each
 * cell is identified by its address, which consists of a set of
 * dimension -> label pairs, where both dimension and label is a
 * string on the form of an identifier or integer.
 */
class TensorBuilder
{
public:
    using Dimension = uint32_t;
    virtual ~TensorBuilder() { }

    virtual Dimension define_dimension(const vespalib::string &dimension) = 0;
    virtual TensorBuilder &
    add_label(Dimension dimension, const vespalib::string &label) = 0;
    virtual TensorBuilder &add_cell(double value) = 0;
    virtual std::unique_ptr<Tensor> build() = 0;
};

} // namespace vespalib::tensor
} // namespace vespalib
