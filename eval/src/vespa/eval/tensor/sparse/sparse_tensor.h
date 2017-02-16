// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/tensor/cell_function.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/tensor_address.h>
#include "sparse_tensor_address_ref.h"
#include <vespa/eval/tensor/types.h>
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
    using Cells = vespalib::hash_map<SparseTensorAddressRef, double>;

    static constexpr size_t STASH_CHUNK_SIZE = 16384u;

private:
    eval::ValueType _type;
    Cells _cells;
    Stash _stash;

public:
    explicit SparseTensor(const eval::ValueType &type_in,
                             const Cells &cells_in);
    SparseTensor(eval::ValueType &&type_in,
                    Cells &&cells_in, Stash &&stash_in);
    const Cells &cells() const { return _cells; }
    const eval::ValueType &type() const { return _type; }
    bool operator==(const SparseTensor &rhs) const;
    eval::ValueType combineDimensionsWith(const SparseTensor &rhs) const;

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
