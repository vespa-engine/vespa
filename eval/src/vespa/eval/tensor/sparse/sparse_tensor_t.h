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

template<typename T>
class SparseTensorT : public SparseTensor
{
private:
    std::vector<T> _values;
public:
    SparseTensorT(eval::ValueType type_in, SparseTensorIndex index_in, std::vector<T> cells_in);
    ~SparseTensorT() override;
    TypedCells cells() const override;
    T get_value(size_t idx) const { return _values[idx]; }
    size_t my_size() const { return _values.size(); }
    const std::vector<T> &my_values() const { return _values; }
    double as_double() const override;
    void accept(TensorVisitor &visitor) const override;
    Tensor::UP add(const Tensor &arg) const override;
    Tensor::UP apply(const CellFunction &func) const override;
    Tensor::UP clone() const override;
    Tensor::UP join(join_fun_t function, const Tensor &arg) const override;
    Tensor::UP merge(join_fun_t function, const Tensor &arg) const override;
    Tensor::UP modify(join_fun_t op, const CellValues &cellValues) const override;
    Tensor::UP reduce(join_fun_t op, const std::vector<vespalib::string> &dimensions) const override;
    Tensor::UP remove(const CellValues &cellAddresses) const override;
    MemoryUsage get_memory_usage() const override;
};

}
