// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor.h"
#include "sparse_tensor_address_builder.h"
#include "sparse_tensor_match.h"
#include "sparse_tensor_apply.hpp"
#include "sparse_tensor_reduce.hpp"
#include <vespa/eval/tensor/tensor_address_builder.h>
#include <vespa/eval/tensor/tensor_apply.h>
#include <vespa/eval/tensor/tensor_visitor.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_map_equal.hpp>
#include <vespa/vespalib/util/array_equal.hpp>

using vespalib::eval::TensorSpec;

namespace vespalib::tensor {

namespace {

using Cells = SparseTensor::Cells;

void
copyCells(Cells &cells, const Cells &cells_in, Stash &stash)
{
    for (const auto &cell : cells_in) {
        SparseTensorAddressRef oldRef = cell.first;
        SparseTensorAddressRef newRef(oldRef, stash);
        cells[newRef] = cell.second;
    }
}

}

SparseTensor::SparseTensor(const eval::ValueType &type_in, const Cells &cells_in)
    : _type(type_in),
      _cells(),
      _stash(STASH_CHUNK_SIZE)
{
    copyCells(_cells, cells_in, _stash);
}


SparseTensor::SparseTensor(eval::ValueType &&type_in, Cells &&cells_in, Stash &&stash_in)
    : _type(std::move(type_in)),
      _cells(std::move(cells_in)),
      _stash(std::move(stash_in))
{ }

SparseTensor::~SparseTensor() = default;

bool
SparseTensor::operator==(const SparseTensor &rhs) const
{
    return _type == rhs._type && _cells == rhs._cells;
}


eval::ValueType
SparseTensor::combineDimensionsWith(const SparseTensor &rhs) const
{
    std::vector<eval::ValueType::Dimension> result;
    std::set_union(_type.dimensions().cbegin(), _type.dimensions().cend(),
                   rhs._type.dimensions().cbegin(), rhs._type.dimensions().cend(),
                   std::back_inserter(result),
                   [](const eval::ValueType::Dimension &lhsDim,
                      const eval::ValueType::Dimension &rhsDim)
                   { return lhsDim.name < rhsDim.name; });
    return (result.empty() ?
            eval::ValueType::double_type() :
            eval::ValueType::tensor_type(std::move(result)));
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
    _cells.for_each([&result](const auto & v) { result += v.second; });
    return result;
}

Tensor::UP
SparseTensor::apply(const CellFunction &func) const
{
    return TensorApply<SparseTensor>(*this, func).result();
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
    return std::make_unique<SparseTensor>(_type, _cells);
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
    for (const auto &cell : _cells) {
        SparseTensorAddressDecoder decoder(cell.first);
        buildAddress(_type, decoder, address);
        result.add(address, cell.second);
        address.clear();
    }
    if (_type.dimensions().empty() && _cells.empty()) {
        result.add(address, 0.0);
    }
    return result;
}

void
SparseTensor::accept(TensorVisitor &visitor) const
{
    TensorAddressBuilder addrBuilder;
    TensorAddress addr;
    for (const auto &cell : _cells) {
        SparseTensorAddressDecoder decoder(cell.first);
        addrBuilder.clear();
        for (const auto &dimension : _type.dimensions()) {
            auto label = decoder.decodeLabel();
            if (label.size() != 0u) {
                addrBuilder.add(dimension.name, label);
            }
        }
        assert(!decoder.valid());
        addr = addrBuilder.build();
        visitor.visit(addr, cell.second);
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
            return sparse::apply(*this, *rhs, [](double lhsValue, double rhsValue)
                                 { return lhsValue * rhsValue; });
        }
    }
    return sparse::apply(*this, *rhs, function);
}

Tensor::UP
SparseTensor::reduce(join_fun_t op,
                     const std::vector<vespalib::string> &dimensions) const
{
    return sparse::reduce(*this, dimensions, op);
}

}

VESPALIB_HASH_MAP_INSTANTIATE_H_E_M(vespalib::tensor::SparseTensorAddressRef, double, vespalib::hash<vespalib::tensor::SparseTensorAddressRef>,
        std::equal_to<vespalib::tensor::SparseTensorAddressRef>, vespalib::hashtable_base::and_modulator);
