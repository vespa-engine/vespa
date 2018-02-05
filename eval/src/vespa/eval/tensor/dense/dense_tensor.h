// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/eval/value_type.h>
#include "dense_tensor_cells_iterator.h"
#include "dense_tensor_view.h"

namespace vespalib::tensor {

/**
 * A dense tensor where all dimensions are indexed.
 * Tensor cells are stored in an underlying array according to the order of the dimensions.
 */
class DenseTensor : public DenseTensorView
{
public:
    typedef std::unique_ptr<DenseTensor> UP;
    using Cells = std::vector<double>;

private:
    eval::ValueType _type;
    Cells           _cells;

public:
    DenseTensor();
    ~DenseTensor() {}
    DenseTensor(const eval::ValueType &type_in, const Cells &cells_in);
    DenseTensor(const eval::ValueType &type_in, Cells &&cells_in);
    DenseTensor(eval::ValueType &&type_in, Cells &&cells_in);
    bool operator==(const DenseTensor &rhs) const;
};

}

