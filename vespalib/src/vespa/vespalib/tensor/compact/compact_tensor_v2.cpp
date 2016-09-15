// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "compact_tensor_v2.h"
#include "compact_tensor_v2_address_builder.h"
#include "compact_tensor_v2_dimension_sum.h"
#include "compact_tensor_v2_match.h"
#include "compact_tensor_v2_product.h"
#include "join_compact_tensors_v2.h"
#include <vespa/vespalib/tensor/tensor_apply.h>
#include <vespa/vespalib/tensor/tensor_visitor.h>
#include <sstream>


namespace vespalib {
namespace tensor {

namespace {

using Cells = CompactTensorV2::Cells;

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

CompactTensorV2::CompactTensorV2(const Dimensions &dimensions_in,
                                 const Cells &cells_in)
    : _cells(),
      _dimensions(dimensions_in),
      _stash(STASH_CHUNK_SIZE)
{
    copyCells(_cells, cells_in, _stash);
}


CompactTensorV2::CompactTensorV2(Dimensions &&dimensions_in,
                                 Cells &&cells_in, Stash &&stash_in)
    : _cells(std::move(cells_in)),
      _dimensions(std::move(dimensions_in)),
      _stash(std::move(stash_in))
{
}


bool
CompactTensorV2::operator==(const CompactTensorV2 &rhs) const
{
    return _dimensions == rhs._dimensions && _cells == rhs._cells;
}


CompactTensorV2::Dimensions
CompactTensorV2::combineDimensionsWith(const CompactTensorV2 &rhs) const
{
    Dimensions result;
    std::set_union(_dimensions.cbegin(), _dimensions.cend(),
                   rhs._dimensions.cbegin(), rhs._dimensions.cend(),
                   std::back_inserter(result));
    return result;
}

eval::ValueType
CompactTensorV2::getType() const
{
    if (_dimensions.empty()) {
        return eval::ValueType::double_type();
    }
    std::vector<eval::ValueType::Dimension> dimensions;
    std::copy(_dimensions.begin(), _dimensions.end(), std::back_inserter(dimensions));
    return eval::ValueType::tensor_type(dimensions);
}

double
CompactTensorV2::sum() const
{
    double result = 0.0;
    for (const auto &cell : _cells) {
        result += cell.second;
    }
    return result;
}

Tensor::UP
CompactTensorV2::add(const Tensor &arg) const
{
    const CompactTensorV2 *rhs = dynamic_cast<const CompactTensorV2 *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return joinCompactTensorsV2(*this, *rhs,
            [](double lhsValue, double rhsValue) { return lhsValue + rhsValue; });
}

Tensor::UP
CompactTensorV2::subtract(const Tensor &arg) const
{
    const CompactTensorV2 *rhs = dynamic_cast<const CompactTensorV2 *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    // Note that -rhsCell.second is passed to the lambda function, that is why we do addition.
    return joinCompactTensorsV2Negated(*this, *rhs,
            [](double lhsValue, double rhsValue) { return lhsValue + rhsValue; });
}

Tensor::UP
CompactTensorV2::multiply(const Tensor &arg) const
{
    const CompactTensorV2 *rhs = dynamic_cast<const CompactTensorV2 *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return CompactTensorV2Product(*this, *rhs).result();
}

Tensor::UP
CompactTensorV2::min(const Tensor &arg) const
{
    const CompactTensorV2 *rhs = dynamic_cast<const CompactTensorV2 *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return joinCompactTensorsV2(*this, *rhs,
            [](double lhsValue, double rhsValue) { return std::min(lhsValue, rhsValue); });
}

Tensor::UP
CompactTensorV2::max(const Tensor &arg) const
{
    const CompactTensorV2 *rhs = dynamic_cast<const CompactTensorV2 *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return joinCompactTensorsV2(*this, *rhs,
            [](double lhsValue, double rhsValue) { return std::max(lhsValue, rhsValue); });
}

Tensor::UP
CompactTensorV2::match(const Tensor &arg) const
{
    const CompactTensorV2 *rhs = dynamic_cast<const CompactTensorV2 *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return CompactTensorV2Match(*this, *rhs).result();
}

Tensor::UP
CompactTensorV2::apply(const CellFunction &func) const
{
    return TensorApply<CompactTensorV2>(*this, func).result();
}

Tensor::UP
CompactTensorV2::sum(const vespalib::string &dimension) const
{
    return CompactTensorV2DimensionSum(*this, dimension).result();
}

bool
CompactTensorV2::equals(const Tensor &arg) const
{
    const CompactTensorV2 *rhs = dynamic_cast<const CompactTensorV2 *>(&arg);
    if (!rhs) {
        return false;
    }
    return *this == *rhs;
}

vespalib::string
CompactTensorV2::toString() const
{
    std::ostringstream stream;
    stream << *this;
    return stream.str();
}

Tensor::UP
CompactTensorV2::clone() const
{
    return std::make_unique<CompactTensorV2>(_dimensions, _cells);
}

void
CompactTensorV2::print(std::ostream &out) const
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
CompactTensorV2::accept(TensorVisitor &visitor) const
{
    TensorAddressBuilder addrBuilder;
    TensorAddress addr;
    for (const auto &cell : _cells) {
        CompactTensorV2AddressDecoder decoder(cell.first);
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
