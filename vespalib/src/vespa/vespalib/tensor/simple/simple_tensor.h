// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/tensor/cell_function.h>
#include <vespa/vespalib/tensor/tensor.h>
#include <vespa/vespalib/tensor/tensor_address.h>
#include <vespa/vespalib/tensor/types.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/string.h>
#include <set>

namespace vespalib {
namespace tensor {

/**
 * A sparse multi-dimensional array.
 *
 * A sparse tensor is a set of cells containing scalar values.
 * Each cell is identified by its address, which consists of a set of dimension -> label pairs,
 * where both dimension and label is a string on the form of an identifier or integer.
 */
class SimpleTensor : public Tensor
{
public:
    typedef std::unique_ptr<SimpleTensor> UP;
    typedef vespalib::hash_map<TensorAddress, double> Cells;
    typedef TensorDimensions Dimensions;

private:
    Dimensions _dimensions;
    Cells _cells;

public:
    SimpleTensor(const Dimensions &dimensions_in, const Cells &cells_in);
    SimpleTensor(Dimensions &&dimensions_in, Cells &&cells_in);
    const Cells &cells() const { return _cells; }
    const Dimensions &dimensions() const { return _dimensions; }
    bool operator==(const SimpleTensor &rhs) const;
    Dimensions combineDimensionsWith(const SimpleTensor &rhs) const;

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
    virtual bool equals(const Tensor &arg) const override;
    virtual void print(std::ostream &out) const override;
    virtual vespalib::string toString() const override;
    virtual Tensor::UP clone() const override;
    virtual void accept(TensorVisitor &visitor) const override;
};

} // namespace vespalib::tensor
} // namespace vespalib
