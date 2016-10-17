// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/tensor/tensor.h>
#include <vespa/vespalib/tensor/types.h>
#include <vespa/vespalib/eval/value_type.h>
#include "dense_tensor_cells_iterator.h"

namespace vespalib {
namespace tensor {

/**
 * A dense tensor where all dimensions are indexed.
 * Tensor cells are stored in an underlying array according to the order of the dimensions.
 */
class DenseTensor : public Tensor
{
public:
    typedef std::unique_ptr<DenseTensor> UP;
    using Cells = std::vector<double>;
    using CellsIterator = DenseTensorCellsIterator;

private:
    eval::ValueType _type;
    Cells _cells;

public:
    DenseTensor();
    DenseTensor(const eval::ValueType &type_in,
                const Cells &cells_in);
    DenseTensor(const eval::ValueType &type_in,
                Cells &&cells_in);
    DenseTensor(eval::ValueType &&type_in,
                Cells &&cells_in);
    const eval::ValueType &type() const { return _type; }
    const Cells &cells() const { return _cells; }
    bool operator==(const DenseTensor &rhs) const;
    CellsIterator cellsIterator() const { return CellsIterator(_type, _cells); }

    virtual eval::ValueType getType() const override;
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
