// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <sstream>

using vespalib::eval::TensorSpec;

namespace vespalib {
namespace tensor {

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

void
printAddress(std::ostream &out, const SparseTensorAddressRef &ref,
             const eval::ValueType &type)
{
    out << "{";
    bool first = true;
    SparseTensorAddressDecoder addr(ref);
    for (auto &dim : type.dimensions()) {
        auto label = addr.decodeLabel();
        if (label.size() != 0u) {
            if (!first) {
                out << ",";
            }
            out << dim.name << ":" << label;
            first = false;
        }
    }
    assert(!addr.valid());
    out << "}";
}

}

SparseTensor::SparseTensor(const eval::ValueType &type_in,
                           const Cells &cells_in)
    : _type(type_in),
      _cells(),
      _stash(STASH_CHUNK_SIZE)
{
    copyCells(_cells, cells_in, _stash);
}


SparseTensor::SparseTensor(eval::ValueType &&type_in,
                           Cells &&cells_in, Stash &&stash_in)
    : _type(std::move(type_in)),
      _cells(std::move(cells_in)),
      _stash(std::move(stash_in))
{
}


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

eval::ValueType
SparseTensor::getType() const
{
    return _type;
}

double
SparseTensor::sum() const
{
    double result = 0.0;
    for (const auto &cell : _cells) {
        result += cell.second;
    }
    return result;
}

Tensor::UP
SparseTensor::add(const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return sparse::apply(*this, *rhs, [](double lhsValue, double rhsValue)
                         { return lhsValue + rhsValue; });
}

Tensor::UP
SparseTensor::subtract(const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return sparse::apply(*this, *rhs, [](double lhsValue, double rhsValue)
                         { return lhsValue - rhsValue; });
}

Tensor::UP
SparseTensor::multiply(const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return sparse::apply(*this, *rhs, [](double lhsValue, double rhsValue)
                         { return lhsValue * rhsValue; });
}

Tensor::UP
SparseTensor::min(const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return sparse::apply(*this, *rhs, [](double lhsValue, double rhsValue)
                         { return std::min(lhsValue, rhsValue); });
}

Tensor::UP
SparseTensor::max(const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return sparse::apply(*this, *rhs, [](double lhsValue, double rhsValue)
                         { return std::max(lhsValue, rhsValue); });
}

Tensor::UP
SparseTensor::match(const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return SparseTensorMatch(*this, *rhs).result();
}

Tensor::UP
SparseTensor::apply(const CellFunction &func) const
{
    return TensorApply<SparseTensor>(*this, func).result();
}

Tensor::UP
SparseTensor::sum(const vespalib::string &dimension) const
{
    return sparse::reduce(*this, { dimension },
                          [](double lhsValue, double rhsValue)
                          { return lhsValue + rhsValue; });
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

vespalib::string
SparseTensor::toString() const
{
    std::ostringstream stream;
    stream << *this;
    return stream.str();
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
    TensorSpec result(getType().to_spec());
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
SparseTensor::print(std::ostream &out) const
{
    out << "{ ";
    bool first = true;
    for (const auto &cell : cells()) {
        if (!first) {
            out << ", ";
        }
        printAddress(out, cell.first, _type);
        out << ":" << cell.second;
        first = false;
    }
    out << " }";
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
SparseTensor::apply(const eval::BinaryOperation &op, const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return sparse::apply(*this, *rhs,
                         [&op](double lhsValue, double rhsValue)
                         { return op.eval(lhsValue, rhsValue); });
}

Tensor::UP
SparseTensor::reduce(const eval::BinaryOperation &op,
                     const std::vector<vespalib::string> &dimensions) const
{
    return sparse::reduce(*this,
                          dimensions,
                          [&op](double lhsValue, double rhsValue)
                          { return op.eval(lhsValue, rhsValue); });
}

} // namespace vespalib::tensor

} // namespace vespalib

VESPALIB_HASH_MAP_INSTANTIATE(vespalib::tensor::SparseTensorAddressRef, double);
