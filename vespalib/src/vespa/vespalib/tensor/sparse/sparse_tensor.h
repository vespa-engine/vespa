// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/tensor/cell_function.h>
#include <vespa/vespalib/tensor/tensor.h>
#include <vespa/vespalib/tensor/tensor_address.h>
#include "compact_tensor_address.h"
#include <vespa/vespalib/tensor/types.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/stash.h>

namespace vespalib {
namespace tensor {

/**
 * A tensor implementation using serialized tensor addresses to
 * improve CPU cache and TLB hit ratio, relative to SimpleTensor
 * implementation.
 */
class SparseTensor : public Tensor
{
public:
    typedef vespalib::hash_map<CompactTensorAddressRef, double> Cells;
    typedef TensorDimensions Dimensions;

    static constexpr size_t STASH_CHUNK_SIZE = 16384u;

private:
    Cells _cells;
    Dimensions _dimensions;
    Stash _stash;

public:
    explicit SparseTensor(const Dimensions &dimensions_in,
                             const Cells &cells_in);
    SparseTensor(Dimensions &&dimensions_in,
                    Cells &&cells_in, Stash &&stash_in);
    const Cells &cells() const { return _cells; }
    const Dimensions &dimensions() const { return _dimensions; }
    bool operator==(const SparseTensor &rhs) const;
    Dimensions combineDimensionsWith(const SparseTensor &rhs) const;

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
