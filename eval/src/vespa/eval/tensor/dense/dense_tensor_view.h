// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

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
    enum class SerializeFormat {FLOAT, DOUBLE};
    using Cells = std::vector<double>;
    using CellsRef = ConstArrayRef<double>;
    using CellsIterator = DenseTensorCellsIterator;
    using Address = std::vector<eval::ValueType::Dimension::size_type>;

    DenseTensorView(const eval::ValueType &type_in, CellsRef cells_in)
        : _typeRef(type_in),
          _cellsRef(cells_in),
          _serializeFormat(SerializeFormat::DOUBLE)
    {}
    explicit DenseTensorView(const eval::ValueType &type_in)
        : _typeRef(type_in),
          _cellsRef(),
          _serializeFormat(SerializeFormat::DOUBLE)
    {}
    SerializeFormat serializeAs() const { return _serializeFormat; }
    void serializeAs(SerializeFormat format) { _serializeFormat = format; }
    const eval::ValueType &fast_type() const { return _typeRef; }
    const CellsRef &cellsRef() const { return _cellsRef; }
    bool operator==(const DenseTensorView &rhs) const;
    CellsIterator cellsIterator() const { return CellsIterator(_typeRef, _cellsRef); }

    const eval::ValueType &type() const override;
    double as_double() const override;
    Tensor::UP apply(const CellFunction &func) const override;
    Tensor::UP join(join_fun_t function, const Tensor &arg) const override;
    Tensor::UP reduce(join_fun_t op, const std::vector<vespalib::string> &dimensions) const override;
    std::unique_ptr<Tensor> modify(join_fun_t op, const CellValues &cellValues) const override;
    std::unique_ptr<Tensor> add(const Tensor &arg) const override;
    std::unique_ptr<Tensor> remove(const CellValues &) const override;
    bool equals(const Tensor &arg) const override;
    Tensor::UP clone() const override;
    eval::TensorSpec toSpec() const override;
    void accept(TensorVisitor &visitor) const override;
protected:
    void initCellsRef(CellsRef cells_in) {
        _cellsRef = cells_in;
    }
private:
    Tensor::UP reduce_all(join_fun_t op, const std::vector<vespalib::string> &dimensions) const;

    const eval::ValueType &_typeRef;
    CellsRef               _cellsRef;
    //TODO This is a temporary workaround until proper type support for tensors is in place.
    SerializeFormat        _serializeFormat;
};

}
