// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/eval/value_type.h>
#include "dense_tensor_cells_iterator.h"

namespace vespalib::tensor {

class DenseTensor;

/**
 * A view to a dense tensor where all dimensions are indexed.
 * Tensor cells are stored in an underlying array according to the order of the dimensions.
 */
class DenseTensorView : public Tensor
{
public:
    using Cells = std::vector<double>;
    using CellsRef = ConstArrayRef<double>;
    using CellsIterator = DenseTensorCellsIterator;
    using Address = std::vector<eval::ValueType::Dimension::size_type>;

private:
    const eval::ValueType &_typeRef;
protected:
    CellsRef _cellsRef;

    void initCellsRef(CellsRef cells_in) {
        _cellsRef = cells_in;
    }

public:
    explicit DenseTensorView(const DenseTensor &rhs);
    DenseTensorView(const eval::ValueType &type_in, CellsRef cells_in)
        : _typeRef(type_in),
          _cellsRef(cells_in)
    {}
    DenseTensorView(const eval::ValueType &type_in)
            : _typeRef(type_in),
              _cellsRef()
    {}
    const eval::ValueType &fast_type() const { return _typeRef; }
    const CellsRef &cellsRef() const { return _cellsRef; }
    bool operator==(const DenseTensorView &rhs) const;
    CellsIterator cellsIterator() const { return CellsIterator(_typeRef, _cellsRef); }

    const eval::ValueType &type() const override;
    double as_double() const override;
    Tensor::UP apply(const CellFunction &func) const override;
    Tensor::UP join(join_fun_t function, const Tensor &arg) const override;
    Tensor::UP reduce(join_fun_t op, const std::vector<vespalib::string> &dimensions) const override;
    bool equals(const Tensor &arg) const override;
    Tensor::UP clone() const override;
    eval::TensorSpec toSpec() const override;
    void accept(TensorVisitor &visitor) const override;
};

}
