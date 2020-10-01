// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor.h"
#include "sparse_tensor_add.h"
#include "sparse_tensor_address_builder.h"
#include "sparse_tensor_join.hpp"
#include "sparse_tensor_match.h"
#include "direct_sparse_tensor_builder.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/tensor/cell_values.h>
#include <vespa/eval/tensor/tensor_address_builder.h>
#include <vespa/eval/tensor/tensor_visitor.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_map_equal.hpp>
#include <vespa/vespalib/util/array_equal.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".eval.tensor.sparse.sparse_tensor");

using vespalib::eval::TensorSpec;

namespace vespalib::tensor {

SparseTensor::SparseTensor(eval::ValueType type_in, SparseTensorIndex index_in)
    : _type(std::move(type_in)),
      _index(std::move(index_in))
{}

SparseTensor::~SparseTensor() = default;

struct CompareValues {
    template <typename T>
    static bool invoke(const SparseTensor &lhs_in,
                       const SparseTensor &rhs_in)
    {
        auto & lhs = static_cast<const SparseTensorT<T> &>(lhs_in);
        auto & rhs = static_cast<const SparseTensorT<T> &>(rhs_in);
        auto lhs_cells = lhs.cells().template typify<T>();
        auto rhs_cells = rhs.cells().template typify<T>();
        size_t rhs_idx;
        for (const auto & kv : lhs.index().get_map()) {
            if (rhs.index().lookup_address(kv.first, rhs_idx)) {
                size_t lhs_idx = kv.second;
                if (lhs_cells[lhs_idx] != rhs_cells[rhs_idx]) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }
};

bool
SparseTensor::operator==(const SparseTensor &rhs) const
{
    if (fast_type() == rhs.fast_type() && my_size() == rhs.my_size()) {
        return typify_invoke<1,eval::TypifyCellType,CompareValues>(_type.cell_type(), *this, rhs);
    }
    return false;
}

eval::ValueType
SparseTensor::combineDimensionsWith(const SparseTensor &rhs) const
{
    return eval::ValueType::join(_type, rhs._type);
}

const eval::ValueType &
SparseTensor::type() const
{
    return _type;
}

bool
SparseTensor::equals(const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    if (!rhs) {
        return false;
    }
    return *this == *rhs;
}

TensorSpec
SparseTensor::toSpec() const
{
    return vespalib::eval::spec_from_value(*this);
}



}

VESPALIB_HASH_MAP_INSTANTIATE(vespalib::tensor::SparseTensorAddressRef, double);
