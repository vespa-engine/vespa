// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor_index.h"
#include <vespa/eval/tensor/cell_function.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/tensor_address.h>
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
private:
    eval::ValueType _type;
    SparseTensorIndex _index;
    std::vector<double> _values;

public:
    SparseTensor(eval::ValueType type_in, SparseTensorIndex index_in, std::vector<double> cells_in);
    ~SparseTensor() override;
    TypedCells cells() const override;
    const std::vector<double> &my_values() const { return _values; }
    const SparseTensorIndex &index() const override { return _index; }
    const eval::ValueType &fast_type() const { return _type; }
    bool operator==(const SparseTensor &rhs) const;
    eval::ValueType combineDimensionsWith(const SparseTensor &rhs) const;

    const eval::ValueType &type() const override;
    double as_double() const override;
    Tensor::UP apply(const CellFunction &func) const override;
    Tensor::UP join(join_fun_t function, const Tensor &arg) const override;
    Tensor::UP merge(join_fun_t function, const Tensor &arg) const override;
    Tensor::UP reduce(join_fun_t op, const std::vector<vespalib::string> &dimensions) const override;
    std::unique_ptr<Tensor> modify(join_fun_t op, const CellValues &cellValues) const override;
    std::unique_ptr<Tensor> add(const Tensor &arg) const override;
    std::unique_ptr<Tensor> remove(const CellValues &cellAddresses) const override;
    bool equals(const Tensor &arg) const override;
    Tensor::UP clone() const override;
    eval::TensorSpec toSpec() const override;
    void accept(TensorVisitor &visitor) const override;
    MemoryUsage get_memory_usage() const override;
};

}
