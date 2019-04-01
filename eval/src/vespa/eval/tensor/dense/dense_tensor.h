// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

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
    ~DenseTensor() override;
    DenseTensor(const eval::ValueType &type_in, const Cells &cells_in);
    DenseTensor(const eval::ValueType &type_in, Cells &&cells_in);
    DenseTensor(eval::ValueType &&type_in, Cells &&cells_in);
    bool operator==(const DenseTensor &rhs) const;
};

}

