// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor.h"
#include "sparse_tensor_add.h"
#include "sparse_tensor_address_builder.h"
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

using vespalib::eval::TensorSpec;

namespace vespalib::tensor {

SparseTensor::SparseTensor(eval::ValueType type_in, SparseTensorIndex index_in, std::vector<double> values_in)
    : _type(std::move(type_in)),
      _index(std::move(index_in)),
      _values(std::move(values_in))
{
}

SparseTensor::~SparseTensor() = default;

TypedCells
SparseTensor::cells() const
{
    assert(_type.cell_type() == CellType::DOUBLE);
    return TypedCells(_values);
}

bool
SparseTensor::operator==(const SparseTensor &rhs) const
{
    return _type == rhs._type
        && _index.get_map() == rhs._index.get_map()
        && _values == rhs._values;
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

double
SparseTensor::as_double() const
{
    double result = 0.0;
    for (double v : _values) {
        result += v;
    }
    return result;
}

Tensor::UP
SparseTensor::apply(const CellFunction &func) const
{
    std::vector<double> new_values;
    new_values.reserve(_values.size());
    for (auto v : _values) {
        new_values.push_back(func.apply(v));
    }
    return std::make_unique<SparseTensor>(_type, _index, std::move(new_values));
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

Tensor::UP
SparseTensor::clone() const
{
    return std::make_unique<SparseTensor>(_type, _index, _values);
}

namespace {

void
buildAddress(const eval::ValueType &type,
             SparseTensorAddressDecoder &decoder,
             TensorSpec::Address &address)
{
    for (const auto &dimension : type.dimensions()) {
        auto label = decoder.decodeLabel();
        address.emplace(std::make_pair(dimension.name, TensorSpec::Label(label)));
    }
    assert(!decoder.valid());
}

}

TensorSpec
SparseTensor::toSpec() const
{
    TensorSpec result(type().to_spec());
    TensorSpec::Address address;
    for (const auto & kv : _index.get_map()) {
        SparseTensorAddressDecoder decoder(kv.first);
        buildAddress(_type, decoder, address);
        result.add(address, _values[kv.second]);
        address.clear();
    }
    if (_type.dimensions().empty() && _values.empty()) {
        result.add(address, 0.0);
    }
    return result;
}

void
SparseTensor::accept(TensorVisitor &visitor) const
{
    TensorAddressBuilder addrBuilder;
    TensorAddress addr;
    for (const auto & kv : _index.get_map()) {
        SparseTensorAddressDecoder decoder(kv.first);
        addrBuilder.clear();
        for (const auto &dimension : _type.dimensions()) {
            auto label = decoder.decodeLabel();
            if (label.size() != 0u) {
                addrBuilder.add(dimension.name, label);
            }
        }
        assert(!decoder.valid());
        addr = addrBuilder.build();
        visitor.visit(addr, _values[kv.second]);
    }
}

Tensor::UP
SparseTensor::join(join_fun_t function, const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    if (function == eval::operation::Mul::f) {
        if (fast_type() == rhs->fast_type()) {
            return SparseTensorMatch(*this, *rhs).result();
        } else {
            return sparse::join(*this, *rhs, [](double lhsValue, double rhsValue)
                                { return lhsValue * rhsValue; });
        }
    }
    return sparse::join(*this, *rhs, function);
}

Tensor::UP
SparseTensor::merge(join_fun_t function, const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    assert(rhs && (fast_type().dimensions() == rhs->fast_type().dimensions()));
    DirectSparseTensorBuilder builder(eval::ValueType::merge(fast_type(), rhs->fast_type()));
    builder.reserve(_values.size() + rhs->my_values().size());

    const auto &lhs_map = _index.get_map();
    const auto &rhs_map = rhs->_index.get_map();

    for (const auto & kv : _index.get_map()) {
        auto pos = rhs_map.find(kv.first);
        if (pos == rhs_map.end()) {
            builder.insertCell(kv.first, _values[kv.second]);
        } else {
            auto a = _values[kv.second];
            auto b = rhs->_values[pos->second];
            builder.insertCell(kv.first, function(a, b));
        }
    }
    for (const auto & kv : rhs_map) {
        auto pos = lhs_map.find(kv.first);
        if (pos == lhs_map.end()) {
            builder.insertCell(kv.first, rhs->_values[kv.second]);
        }
    }
    return builder.build();
}

Tensor::UP
SparseTensor::reduce(join_fun_t op,
                     const std::vector<vespalib::string> &dimensions) const
{
    return sparse::reduce(*this, dimensions, op);
}

std::unique_ptr<Tensor>
SparseTensor::modify(join_fun_t op, const CellValues &cellValues) const
{
    SparseTensorModify modifier(op, _type, _index, _values);
    cellValues.accept(modifier);
    return modifier.build();
}

std::unique_ptr<Tensor>
SparseTensor::add(const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    SparseTensorAdd adder(_type, _index, _values);
    rhs->accept(adder);
    return adder.build();
}

std::unique_ptr<Tensor>
SparseTensor::remove(const CellValues &cellAddresses) const
{
    SparseTensorRemove remover(*this);
    cellAddresses.accept(remover);
    return remover.build();
}

MemoryUsage
SparseTensor::get_memory_usage() const
{
    MemoryUsage result = _index.get_memory_usage();
    result.incUsedBytes(sizeof(SparseTensor));
    result.incUsedBytes(_values.size() * sizeof(double));
    result.incAllocatedBytes(sizeof(SparseTensor));
    result.incAllocatedBytes(_values.capacity() * sizeof(double));
    return result;
}

}

VESPALIB_HASH_MAP_INSTANTIATE(vespalib::tensor::SparseTensorAddressRef, double);
