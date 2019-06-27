// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor_view.h"

namespace vespalib::tensor {

/**
 * A dense tensor where all dimensions are indexed.
 * Tensor cells are stored in an underlying array according to the order of the dimensions.
 */
template <typename CT>
class DenseTensor : public DenseTensorView
{
public:
    DenseTensor() = delete;
    ~DenseTensor() override;
    DenseTensor(eval::ValueType &&type_in, std::vector<CT> &&cells_in);

    // for unit tests
    template <typename RCT>
    bool operator==(const DenseTensor<RCT> &rhs) const;
private:
    eval::ValueType _type;
    std::vector<CT> _cells;
};

}
