// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "compact_tensor.h"
#include "compact_tensor_address_builder.h"
#include "compact_tensor_dimension_sum.h"
#include "compact_tensor_product.h"
#include <vespa/vespalib/tensor/join_tensors.h>
#include <vespa/vespalib/tensor/tensor_apply.h>
#include <vespa/vespalib/tensor/tensor_visitor.h>
#include <sstream>

namespace vespalib {
namespace tensor {

namespace {

void
copyCells(CompactTensor::Cells &cells,
          const CompactTensor::Cells &cells_in,
          Stash &stash)
{
    for (const auto &cell : cells_in) {
        CompactTensorAddressRef oldRef = cell.first;
        CompactTensorAddressRef newRef(oldRef, stash);
        cells[newRef] = cell.second;
    }
}

}

CompactTensor::CompactTensor(const Dimensions &dimensions_in,
                             const Cells &cells_in)
    : _cells(),
      _dimensions(dimensions_in),
      _stash(STASH_CHUNK_SIZE)
{
    copyCells(_cells, cells_in, _stash);
}


CompactTensor::CompactTensor(Dimensions &&dimensions_in,
                             Cells &&cells_in, Stash &&stash_in)
    : _cells(std::move(cells_in)),
      _dimensions(std::move(dimensions_in)),
      _stash(std::move(stash_in))
{
}


bool
CompactTensor::operator==(const CompactTensor &rhs) const
{
    return _dimensions == rhs._dimensions && _cells == rhs._cells;
}


CompactTensor::Dimensions
CompactTensor::combineDimensionsWith(const CompactTensor &rhs) const
{
    Dimensions result;
    std::set_union(_dimensions.cbegin(), _dimensions.cend(),
                   rhs._dimensions.cbegin(), rhs._dimensions.cend(),
                   std::back_inserter(result));
    return result;
}

eval::ValueType
CompactTensor::getType() const
{
    if (_dimensions.empty()) {
        return eval::ValueType::double_type();
    }
    std::vector<eval::ValueType::Dimension> dimensions;
    std::copy(_dimensions.begin(), _dimensions.end(), std::back_inserter(dimensions));
    return eval::ValueType::tensor_type(dimensions);
}

double
CompactTensor::sum() const
{
    double result = 0.0;
    for (const auto &cell : _cells) {
        result += cell.second;
    }
    return result;
}

Tensor::UP
CompactTensor::add(const Tensor &arg) const
{
    const CompactTensor *rhs = dynamic_cast<const CompactTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return joinTensors(*this, *rhs,
            [](double lhsValue, double rhsValue) { return lhsValue + rhsValue; });
}

Tensor::UP
CompactTensor::subtract(const Tensor &arg) const
{
    const CompactTensor *rhs = dynamic_cast<const CompactTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return joinTensorsNegated(*this, *rhs,
            [](double lhsValue, double rhsValue) { return lhsValue + rhsValue; });
    // Note that -rhsCell.second is passed to the lambda function, that is why we do addition.
}

Tensor::UP
CompactTensor::multiply(const Tensor &arg) const
{
    const CompactTensor *rhs = dynamic_cast<const CompactTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return CompactTensorProduct(*this, *rhs).result();
}

Tensor::UP
CompactTensor::min(const Tensor &arg) const
{
    const CompactTensor *rhs = dynamic_cast<const CompactTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return joinTensors(*this, *rhs,
            [](double lhsValue, double rhsValue) { return std::min(lhsValue, rhsValue); });
}

Tensor::UP
CompactTensor::max(const Tensor &arg) const
{
    const CompactTensor *rhs = dynamic_cast<const CompactTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return joinTensors(*this, *rhs,
            [](double lhsValue, double rhsValue) { return std::max(lhsValue, rhsValue); });
}

Tensor::UP
CompactTensor::match(const Tensor &arg) const
{
    const CompactTensor *rhs = dynamic_cast<const CompactTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    DirectTensorBuilder<CompactTensor> builder(combineDimensionsWith(*rhs));
    for (const auto &lhsCell : cells()) {
        auto rhsItr = rhs->cells().find(lhsCell.first);
        if (rhsItr != rhs->cells().end()) {
            builder.insertCell(lhsCell.first, lhsCell.second * rhsItr->second);
        }
    }
    return builder.build();
}

Tensor::UP
CompactTensor::apply(const CellFunction &func) const
{
    return TensorApply<CompactTensor>(*this, func).result();
}

Tensor::UP
CompactTensor::sum(const vespalib::string &dimension) const
{
    return CompactTensorDimensionSum(*this, dimension).result();
}

bool
CompactTensor::equals(const Tensor &arg) const
{
    const CompactTensor *rhs = dynamic_cast<const CompactTensor *>(&arg);
    if (!rhs) {
        return false;
    }
    return *this == *rhs;
}

vespalib::string
CompactTensor::toString() const
{
    std::ostringstream stream;
    stream << *this;
    return stream.str();
}

Tensor::UP
CompactTensor::clone() const
{
    return std::make_unique<CompactTensor>(_dimensions, _cells);
}

void
CompactTensor::print(std::ostream &out) const
{
    out << "{ ";
    bool first = true;
    CompactTensorAddress addr;
    for (const auto &cell : cells()) {
        if (!first) {
            out << ", ";
        }
        addr.deserializeFromSparseAddressRef(cell.first);
        out << addr << ":" << cell.second;
        first = false;
    }
    out << " }";
}

void
CompactTensor::accept(TensorVisitor &visitor) const
{
    CompactTensorAddress caddr;
    TensorAddressBuilder addrBuilder;
    TensorAddress addr;
    for (const auto &cell : _cells) {
        caddr.deserializeFromSparseAddressRef(cell.first);
        addrBuilder.clear();
        for (const auto &element : caddr.elements()) {
            addrBuilder.add(element.dimension(), element.label());
        }
        addr = addrBuilder.build();
        visitor.visit(addr, cell.second);
    }
}

} // namespace vespalib::tensor
} // namespace vespalib
