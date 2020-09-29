// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor_index.h"
#include <vespa/eval/tensor/cell_function.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/tensor_address.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/vespalib/stllike/string.h>

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

public:
    SparseTensor(eval::ValueType type_in, SparseTensorIndex index_in);
    ~SparseTensor() override;
    size_t my_size() const { return _index.get_map().size(); }
    const SparseTensorIndex &index() const override { return _index; }
    const eval::ValueType &fast_type() const { return _type; }
    bool operator==(const SparseTensor &rhs) const;
    eval::ValueType combineDimensionsWith(const SparseTensor &rhs) const;

    const eval::ValueType &type() const override;
    bool equals(const Tensor &arg) const override;
    eval::TensorSpec toSpec() const override;
};

}
