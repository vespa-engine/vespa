// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/eval/value_type.h>
#include "dense_tensor_cells_iterator.h"

namespace vespalib {
namespace tensor {

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
    const eval::ValueType &type() const { return _typeRef; }
    const CellsRef &cellsRef() const { return _cellsRef; }
    bool operator==(const DenseTensorView &rhs) const;
    CellsIterator cellsIterator() const { return CellsIterator(_typeRef, _cellsRef); }

    virtual const eval::ValueType &getType() const override;
    virtual double sum() const override;
    virtual Tensor::UP add(const Tensor &arg) const override;
    virtual Tensor::UP subtract(const Tensor &arg) const override;
    virtual Tensor::UP multiply(const Tensor &arg) const override;
    virtual Tensor::UP min(const Tensor &arg) const override;
    virtual Tensor::UP max(const Tensor &arg) const override;
    virtual Tensor::UP match(const Tensor &arg) const override;
    virtual Tensor::UP apply(const CellFunction &func) const override;
    virtual Tensor::UP sum(const vespalib::string &dimension) const override;
    virtual Tensor::UP apply(const eval::BinaryOperation &op,
                             const Tensor &arg) const override;
    virtual Tensor::UP reduce(const eval::BinaryOperation &op,
                              const std::vector<vespalib::string> &dimensions)
        const override;
    virtual bool equals(const Tensor &arg) const override;
    virtual void print(std::ostream &out) const override;
    virtual vespalib::string toString() const override;
    virtual Tensor::UP clone() const override;
    virtual eval::TensorSpec toSpec() const override;
    virtual void accept(TensorVisitor &visitor) const override;
};

} // namespace vespalib::tensor
} // namespace vespalib
