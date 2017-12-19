// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/tensor/cell_function.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/tensor_address.h>
#include "sparse_tensor_address_ref.h"
#include <vespa/eval/tensor/types.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/stash.h>

namespace vespalib::tensor {

/**
 * A tensor implementation using serialized tensor addresses to
 * improve CPU cache and TLB hit ratio, relative to SimpleTensor
 * implementation.
 */
class SparseTensor : public Tensor
{
public:
    using Cells = hash_map<SparseTensorAddressRef, double, hash<SparseTensorAddressRef>,
            std::equal_to<SparseTensorAddressRef>, hashtable_base::and_modulator>;

    static constexpr size_t STASH_CHUNK_SIZE = 16384u;

private:
    eval::ValueType _type;
    Cells _cells;
    Stash _stash;

public:
    explicit SparseTensor(const eval::ValueType &type_in, const Cells &cells_in);
    SparseTensor(eval::ValueType &&type_in, Cells &&cells_in, Stash &&stash_in);
    ~SparseTensor() override;
    const Cells &cells() const { return _cells; }
    const eval::ValueType &fast_type() const { return _type; }
    bool operator==(const SparseTensor &rhs) const;
    eval::ValueType combineDimensionsWith(const SparseTensor &rhs) const;

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
