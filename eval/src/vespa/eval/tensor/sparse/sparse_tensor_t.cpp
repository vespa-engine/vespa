// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor.h"
#include "sparse_tensor_add.h"
#include "sparse_tensor_address_builder.h"
#include "sparse_tensor_join.h"
#include "sparse_tensor_join.hpp"
#include "sparse_tensor_match.h"
#include "sparse_tensor_modify.h"
#include "sparse_tensor_reduce.hpp"
#include "sparse_tensor_remove.h"
#include "direct_sparse_tensor_builder.h"
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/tensor/cell_values.h>
#include <vespa/eval/tensor/tensor_address_builder.h>
#include <vespa/eval/tensor/tensor_visitor.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_map_equal.hpp>
#include <vespa/vespalib/util/array_equal.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".eval.tensor.sparse.sparse_tensor");

namespace vespalib::tensor {

namespace {

template<typename LCT>
struct GenericSparseJoin {
    template<typename RCT>
    static Tensor::UP invoke(const SparseTensor & lhs_in,
                             const SparseTensor & rhs_in,
                             SparseTensor::join_fun_t func)
    {
        auto & lhs = static_cast<const SparseTensorT<LCT> &>(lhs_in);
        auto & rhs = static_cast<const SparseTensorT<RCT> &>(rhs_in);
        return sparse::join<LCT, RCT>(lhs, rhs, func);
    }
};

template<typename LCT>
struct FastSparseJoin {
    template<typename RCT>
    static Tensor::UP invoke(const SparseTensor & lhs_in,
                             const SparseTensor & rhs_in)
    {
        auto & lhs = static_cast<const SparseTensorT<LCT> &>(lhs_in);
        auto & rhs = static_cast<const SparseTensorT<RCT> &>(rhs_in);
        // Ensure that first tensor to fastMatch has fewest cells.
        if (rhs.my_size() < lhs.my_size()) {
            return SparseTensorMatch(rhs, lhs).result();
        } else {
            return SparseTensorMatch(lhs, rhs).result();
        }
    }
};

struct GenericSparseMerge {
    template<typename LCT, typename RCT>
    static Tensor::UP invoke(const SparseTensor &lhs_in,
                             const SparseTensor &rhs_in,
                             SparseTensor::join_fun_t function)
    {
        using OCT = typename eval::UnifyCellTypes<LCT,RCT>::type;
        auto & lhs= static_cast<const SparseTensorT<LCT> &>(lhs_in);
        auto & rhs= static_cast<const SparseTensorT<RCT> &>(rhs_in);
        DirectSparseTensorBuilder<OCT> builder(eval::ValueType::merge(lhs.fast_type(), rhs.fast_type()));
        builder.reserve(lhs.my_size() + rhs.my_size());
        const auto &lhs_map = lhs.index().get_map();
        const auto &rhs_map = rhs.index().get_map();
        for (const auto & kv : lhs_map) {
            auto pos = rhs_map.find(kv.first);
            if (pos == rhs_map.end()) {
                builder.insertCell(kv.first, lhs.get_value(kv.second));
            } else {
                double a = lhs.get_value(kv.second);
                double b = rhs.get_value(pos->second);
                builder.insertCell(kv.first, function(a, b));
            }
        }
        for (const auto & kv : rhs_map) {
            auto pos = lhs_map.find(kv.first);
            if (pos == lhs_map.end()) {
                double b = rhs.get_value(kv.second);
                builder.insertCell(kv.first, b);
            }
        }
        return builder.build();
    }
};

} // namespace <unnamed>

template<typename T>
SparseTensorT<T>::SparseTensorT(eval::ValueType type_in, SparseTensorIndex index_in, std::vector<T> values_in)
    : SparseTensor(std::move(type_in), std::move(index_in)),
      _values(std::move(values_in))
{
}

template<typename T>
SparseTensorT<T>::~SparseTensorT() = default;

template<typename T>
TypedCells
SparseTensorT<T>::cells() const
{
    return TypedCells(_values);
}

template<typename T>
double
SparseTensorT<T>::as_double() const
{
    double result = 0.0;
    for (double v : _values) {
        result += v;
    }
    return result;
}

template<typename T>
void
SparseTensorT<T>::accept(TensorVisitor &visitor) const
{
    TensorAddressBuilder addrBuilder;
    TensorAddress addr;
    for (const auto & kv : index().get_map()) {
        SparseTensorAddressDecoder decoder(kv.first);
        addrBuilder.clear();
        for (const auto &dimension : fast_type().dimensions()) {
            auto label = decoder.decodeLabel();
            if (label.size() != 0u) {
                addrBuilder.add(dimension.name, label);
            }
        }
        assert(!decoder.valid());
        addr = addrBuilder.build();
        visitor.visit(addr, get_value(kv.second));
    }
}

template<typename T>
std::unique_ptr<Tensor>
SparseTensorT<T>::add(const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    SparseTensorAdd<T> adder(fast_type(), index(), _values);
    rhs->accept(adder);
    return adder.build();
}

template<typename T>
Tensor::UP
SparseTensorT<T>::apply(const CellFunction &func) const
{
    std::vector<T> new_values;
    new_values.reserve(_values.size());
    for (T v : _values) {
        new_values.push_back(func.apply(v));
    }
    return std::make_unique<SparseTensorT<T>>(fast_type(), index(), std::move(new_values));
}

template<typename T>
Tensor::UP
SparseTensorT<T>::clone() const
{
    return std::make_unique<SparseTensorT<T>>(fast_type(), index(), _values);
}

template<typename T>
Tensor::UP
SparseTensorT<T>::join(join_fun_t function, const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    if (function == eval::operation::Mul::f) {
        if (fast_type().dimensions() == rhs->fast_type().dimensions()) {
            return typify_invoke<1,eval::TypifyCellType,FastSparseJoin<T>>(
                    rhs->fast_type().cell_type(),
                    *this, *rhs);
        }
    }
    return typify_invoke<1,eval::TypifyCellType,GenericSparseJoin<T>>(
            rhs->fast_type().cell_type(),
            *this, *rhs, function);
}

template<typename T>
Tensor::UP
SparseTensorT<T>::merge(join_fun_t function, const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    assert(rhs && (fast_type().dimensions() == rhs->fast_type().dimensions()));
    return typify_invoke<2,eval::TypifyCellType,GenericSparseMerge>(
            fast_type().cell_type(), rhs->fast_type().cell_type(),
            *this, *rhs, function);
}

template<typename T>
std::unique_ptr<Tensor>
SparseTensorT<T>::modify(join_fun_t op, const CellValues &cellValues) const
{
    SparseTensorModify modifier(op, *this);;
    cellValues.accept(modifier);
    return modifier.build();
}

template<typename T>
Tensor::UP
SparseTensorT<T>::reduce(join_fun_t op, const std::vector<vespalib::string> &dimensions) const
{
    return sparse::reduce(*this, dimensions, op);
}

template<typename T>
std::unique_ptr<Tensor>
SparseTensorT<T>::remove(const CellValues &cellAddresses) const
{
    SparseTensorRemove<T> remover(*this);
    cellAddresses.accept(remover);
    return remover.build();
}

template<typename T>
MemoryUsage
SparseTensorT<T>::get_memory_usage() const
{
    MemoryUsage result = index().get_memory_usage();
    result.incUsedBytes(sizeof(SparseTensor));
    result.incUsedBytes(_values.size() * sizeof(T));
    result.incAllocatedBytes(sizeof(SparseTensor));
    result.incAllocatedBytes(_values.capacity() * sizeof(T));
    return result;
}

template class SparseTensorT<float>;
template class SparseTensorT<double>;

}

VESPALIB_HASH_MAP_INSTANTIATE(vespalib::tensor::SparseTensorAddressRef, double);
