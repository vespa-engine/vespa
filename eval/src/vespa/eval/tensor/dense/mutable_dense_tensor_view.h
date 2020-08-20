// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor_view.h"
#include <cassert>

namespace vespalib::tensor {

/**
 * A mutable view to a dense tensor where all dimensions are indexed.
 */
class MutableDenseTensorView : public DenseTensorView
{
private:
    eval::ValueType _type;

public:
    MutableDenseTensorView(eval::ValueType type_in);
    MutableDenseTensorView(MutableDenseTensorView &&) = default;
    void setCells(TypedCells cells_in) {
        initCellsRef(cells_in);
    }
};

}
