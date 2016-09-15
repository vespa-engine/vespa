// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "simple_tensor.h"
#include "simple_tensor_dimension_sum.h"
#include "simple_tensor_product.h"
#include <vespa/vespalib/tensor/join_tensors.h>
#include <vespa/vespalib/tensor/tensor_apply.h>
#include <sstream>
#include <vespa/vespalib/tensor/tensor_visitor.h>

namespace vespalib {
namespace tensor {

SimpleTensor::SimpleTensor(const Dimensions &dimensions_in, const Cells &cells_in)
    : _dimensions(dimensions_in),
      _cells(cells_in)
{
}

SimpleTensor::SimpleTensor(Dimensions &&dimensions_in, Cells &&cells_in)
    : _dimensions(std::move(dimensions_in)),
      _cells(std::move(cells_in))
{
}

bool
SimpleTensor::operator==(const SimpleTensor &rhs) const
{
    return _dimensions == rhs._dimensions && _cells == rhs._cells;
}

SimpleTensor::Dimensions
SimpleTensor::combineDimensionsWith(const SimpleTensor &rhs) const
{
    Dimensions result;
    std::set_union(_dimensions.cbegin(), _dimensions.cend(),
                   rhs._dimensions.cbegin(), rhs._dimensions.cend(),
                   std::back_inserter(result));
    return result;
}

eval::ValueType
SimpleTensor::getType() const
{
    std::vector<eval::ValueType::Dimension> dimensions;
    std::copy(_dimensions.begin(), _dimensions.end(), std::back_inserter(dimensions));
    return eval::ValueType::tensor_type(dimensions);
}

double
SimpleTensor::sum() const
{
    double result = 0.0;
    for (const auto &cell : _cells) {
        result += cell.second;
    }
    return result;
}

Tensor::UP
SimpleTensor::add(const Tensor &arg) const
{
    const SimpleTensor *rhs = dynamic_cast<const SimpleTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return joinTensors(*this, *rhs,
            [](double lhsValue, double rhsValue) { return lhsValue + rhsValue; });
}

Tensor::UP
SimpleTensor::subtract(const Tensor &arg) const
{
    const SimpleTensor *rhs = dynamic_cast<const SimpleTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    // Note that -rhsCell.second is passed to the lambda function, that is why we do addition.
    return joinTensorsNegated(*this, *rhs,
            [](double lhsValue, double rhsValue) { return lhsValue + rhsValue; });
}

Tensor::UP
SimpleTensor::multiply(const Tensor &arg) const
{
    const SimpleTensor *rhs = dynamic_cast<const SimpleTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return SimpleTensorProduct(*this, *rhs).result();
}

Tensor::UP
SimpleTensor::min(const Tensor &arg) const
{
    const SimpleTensor *rhs = dynamic_cast<const SimpleTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return joinTensors(*this, *rhs,
            [](double lhsValue, double rhsValue){ return std::min(lhsValue, rhsValue); });
}

Tensor::UP
SimpleTensor::max(const Tensor &arg) const
{
    const SimpleTensor *rhs = dynamic_cast<const SimpleTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return joinTensors(*this, *rhs,
            [](double lhsValue, double rhsValue){ return std::max(lhsValue, rhsValue); });
}

Tensor::UP
SimpleTensor::match(const Tensor &arg) const
{
    const SimpleTensor *rhs = dynamic_cast<const SimpleTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    DirectTensorBuilder<SimpleTensor> builder(combineDimensionsWith(*rhs));
    for (const auto &lhsCell : cells()) {
        auto rhsItr = rhs->cells().find(lhsCell.first);
        if (rhsItr != rhs->cells().end()) {
            builder.insertCell(lhsCell.first, lhsCell.second * rhsItr->second);
        }
    }
    return builder.build();
}

Tensor::UP
SimpleTensor::apply(const CellFunction &func) const
{
    return TensorApply<SimpleTensor>(*this, func).result();
}

Tensor::UP
SimpleTensor::sum(const vespalib::string &dimension) const
{
    return SimpleTensorDimensionSum(*this, dimension).result();
}

bool
SimpleTensor::equals(const Tensor &arg) const
{
    const SimpleTensor *rhs = dynamic_cast<const SimpleTensor *>(&arg);
    if (!rhs) {
        return false;
    }
    return *this == *rhs;
}

vespalib::string
SimpleTensor::toString() const
{
    std::ostringstream stream;
    stream << *this;
    return stream.str();
}

Tensor::UP
SimpleTensor::clone() const
{
    return std::make_unique<SimpleTensor>(_dimensions, _cells);
}

namespace {

TensorAddress
getAddressNotFoundInCells(const SimpleTensor::Dimensions &dimensions,
                          const SimpleTensor::Cells &cells)
{
    TensorDimensionsSet dimensionsNotFoundInCells(dimensions.begin(),
                                                  dimensions.end());
    for (const auto &cell : cells) {
        for (const auto &elem : cell.first.elements()) {
            dimensionsNotFoundInCells.erase(elem.dimension());
        }
    }
    SimpleTensor::Dimensions
        missingDimensions(dimensionsNotFoundInCells.begin(),
                          dimensionsNotFoundInCells.end());
    std::sort(missingDimensions.begin(), missingDimensions.end());
    TensorAddress::Elements elements;
    for (const auto &dimension : missingDimensions) {
        elements.emplace_back(dimension, "-");
    }
    return TensorAddress(elements);
}

void
printCells(const SimpleTensor::Cells &cells, std::ostream &out)
{
    out << "{ ";
    bool first = true;
    for (const auto &cell : cells) {
        if (!first) {
            out << ", ";
        }
        out << cell.first << ":" << cell.second;
        first = false;
    }
    out << " }";
}

}

void
SimpleTensor::print(std::ostream &out) const
{
    // This address represents the extra tensor dimensions that are not
    // explicitly found in the tensor cells.
    TensorAddress extraDimensionsAddress = getAddressNotFoundInCells(dimensions(), cells());
    if (extraDimensionsAddress.elements().empty()) {
        printCells(cells(), out);
    } else {
        out << "( ";
        printCells(cells(), out);
        out << " * ";
        // Multiplying with this cell gives us a way of representing the extra tensor
        // dimensions without having explicit syntax for printing dimensions.
        SimpleTensor::Cells extraDimensionsCell;
        extraDimensionsCell.insert(std::make_pair(extraDimensionsAddress, 1.0));
        printCells(extraDimensionsCell, out);
        out << " )";
    }
}

void
SimpleTensor::accept(TensorVisitor &visitor) const
{
    for (const auto &cell : _cells) {
        visitor.visit(cell.first, cell.second);
    }
}

} // namespace vespalib::tensor
} // namespace vespalib
