// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "typed_cells.h"
#include "dense_tensor_cells_iterator.h"
#include <vespa/eval/tensor/tensor.h>

namespace vespalib::tensor {

/**
 * A view to a dense tensor where all dimensions are indexed.
 * Tensor cells are stored in an underlying array according to the order of the dimensions.
 */
class DenseTensorView : public Tensor
{
public:
    using CellsIterator = DenseTensorCellsIterator;
    using Address = std::vector<eval::ValueType::Dimension::size_type>;

    DenseTensorView(const eval::ValueType &type_in, TypedCells cells_in)
        : _typeRef(type_in),
          _cellsRef(cells_in)
    {
        assert(_typeRef.cell_type() == cells_in.type);
    }

    const eval::ValueType &fast_type() const { return _typeRef; }
    const TypedCells &cellsRef() const { return _cellsRef; }
    bool operator==(const DenseTensorView &rhs) const;
    CellsIterator cellsIterator() const { return CellsIterator(_typeRef, _cellsRef); }

    const eval::ValueType &type() const override;
    double as_double() const override;
    Tensor::UP apply(const CellFunction &func) const override;
    Tensor::UP join(join_fun_t function, const Tensor &arg) const override;
    Tensor::UP merge(join_fun_t function, const Tensor &arg) const override;
    Tensor::UP reduce(join_fun_t op, const std::vector<vespalib::string> &dimensions) const override;
    std::unique_ptr<Tensor> modify(join_fun_t op, const CellValues &cellValues) const override;
    std::unique_ptr<Tensor> add(const Tensor &arg) const override;
    std::unique_ptr<Tensor> remove(const CellValues &) const override;
    bool equals(const Tensor &arg) const override;
    Tensor::UP clone() const override;
    eval::TensorSpec toSpec() const override;
    void accept(TensorVisitor &visitor) const override;

    template <typename T> static ConstArrayRef<T> typify_cells(const eval::Value &self) {
        return static_cast<const DenseTensorView &>(self).cellsRef().typify<T>();
    }
    template <typename T> static ConstArrayRef<T> unsafe_typify_cells(const eval::Value &self) {
        return static_cast<const DenseTensorView &>(self).cellsRef().unsafe_typify<T>();
    }
protected:
    explicit DenseTensorView(const eval::ValueType &type_in)
        : _typeRef(type_in),
          _cellsRef()
    {}

    void initCellsRef(TypedCells cells_in) {
        assert(_typeRef.cell_type() == cells_in.type);
        _cellsRef = cells_in;
    }
private:
    Tensor::UP reduce_all(join_fun_t op, const std::vector<vespalib::string> &dimensions) const;

    const eval::ValueType &_typeRef;
    TypedCells             _cellsRef;
};

}
