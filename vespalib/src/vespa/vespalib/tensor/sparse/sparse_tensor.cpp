// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "sparse_tensor.h"
#include "sparse_tensor_address_builder.h"
#include "sparse_tensor_dimension_sum.h"
#include "sparse_tensor_match.h"
#include "sparse_tensor_product.h"
#include "join_sparse_tensors.h"
#include <vespa/vespalib/tensor/tensor_address_builder.h>
#include <vespa/vespalib/tensor/tensor_apply.h>
#include <vespa/vespalib/tensor/tensor_visitor.h>
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
        CompactTensorAddressRef oldRef = cell.first;
        CompactTensorAddressRef newRef(oldRef, stash);
        cells[newRef] = cell.second;
    }
}

}

SparseTensor::SparseTensor(const Dimensions &dimensions_in,
                                 const Cells &cells_in)
    : _cells(),
      _dimensions(dimensions_in),
      _stash(STASH_CHUNK_SIZE)
{
    copyCells(_cells, cells_in, _stash);
}


SparseTensor::SparseTensor(Dimensions &&dimensions_in,
                                 Cells &&cells_in, Stash &&stash_in)
    : _cells(std::move(cells_in)),
      _dimensions(std::move(dimensions_in)),
      _stash(std::move(stash_in))
{
}


bool
SparseTensor::operator==(const SparseTensor &rhs) const
{
    return _dimensions == rhs._dimensions && _cells == rhs._cells;
}


SparseTensor::Dimensions
SparseTensor::combineDimensionsWith(const SparseTensor &rhs) const
{
    Dimensions result;
    std::set_union(_dimensions.cbegin(), _dimensions.cend(),
                   rhs._dimensions.cbegin(), rhs._dimensions.cend(),
                   std::back_inserter(result));
    return result;
}

eval::ValueType
SparseTensor::getType() const
{
    if (_dimensions.empty()) {
        return eval::ValueType::double_type();
    }
    std::vector<eval::ValueType::Dimension> dimensions;
    std::copy(_dimensions.begin(), _dimensions.end(), std::back_inserter(dimensions));
    return eval::ValueType::tensor_type(dimensions);
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
    return joinSparseTensors(*this, *rhs,
            [](double lhsValue, double rhsValue) { return lhsValue + rhsValue; });
}

Tensor::UP
SparseTensor::subtract(const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    // Note that -rhsCell.second is passed to the lambda function, that is why we do addition.
    return joinSparseTensorsNegated(*this, *rhs,
            [](double lhsValue, double rhsValue) { return lhsValue + rhsValue; });
}

Tensor::UP
SparseTensor::multiply(const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return SparseTensorProduct(*this, *rhs).result();
}

Tensor::UP
SparseTensor::min(const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return joinSparseTensors(*this, *rhs,
            [](double lhsValue, double rhsValue) { return std::min(lhsValue, rhsValue); });
}

Tensor::UP
SparseTensor::max(const Tensor &arg) const
{
    const SparseTensor *rhs = dynamic_cast<const SparseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return joinSparseTensors(*this, *rhs,
            [](double lhsValue, double rhsValue) { return std::max(lhsValue, rhsValue); });
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
    return SparseTensorDimensionSum(*this, dimension).result();
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
    return std::make_unique<SparseTensor>(_dimensions, _cells);
}

namespace {

void
buildAddress(const SparseTensor::Dimensions &dimensions,
             SparseTensorAddressDecoder &decoder,
             TensorSpec::Address &address)
{
    for (const auto &dimension : dimensions) {
        auto label = decoder.decodeLabel();
        if (!label.empty()) {
            address.emplace(std::make_pair(dimension, TensorSpec::Label(label)));
        }
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
        buildAddress(_dimensions, decoder, address);
        result.add(address, cell.second);
        address.clear();
    }
    return result;
}

void
SparseTensor::print(std::ostream &out) const
{
    out << "{ ";
    bool first = true;
    CompactTensorAddress addr;
    for (const auto &cell : cells()) {
        if (!first) {
            out << ", ";
        }
        addr.deserializeFromAddressRefV2(cell.first, _dimensions);
        out << addr << ":" << cell.second;
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
        for (const auto &dimension : _dimensions) {
            auto label = decoder.decodeLabel();
            if (label.size() != 0u) {
                addrBuilder.add(dimension, label);
            }
        }
        assert(!decoder.valid());
        addr = addrBuilder.build();
        visitor.visit(addr, cell.second);
    }
}

} // namespace vespalib::tensor
} // namespace vespalib
