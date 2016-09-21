// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_tensor.h"
#include "dense_tensor_dimension_sum.h"
#include "dense_tensor_product.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/tensor/tensor_address_builder.h>
#include <vespa/vespalib/tensor/tensor_visitor.h>
#include <sstream>


namespace vespalib {
namespace tensor {

namespace {

string
dimensionsMetaAsString(const DenseTensor::DimensionsMeta &dimensionsMeta)
{
    std::ostringstream oss;
    bool first = true;
    oss << "[";
    for (const auto &dimMeta : dimensionsMeta) {
        if (!first) {
            oss << ",";
        }
        first = false;
        oss << dimMeta;
    }
    oss << "]";
    return oss.str();
}

size_t
calcCellsSize(const DenseTensor::DimensionsMeta &dimensionsMeta)
{
    size_t cellsSize = 1;
    for (const auto &dimMeta : dimensionsMeta) {
        cellsSize *= dimMeta.size();
    }
    return cellsSize;
}


void
checkCellsSize(const DenseTensor &arg)
{
    auto cellsSize = calcCellsSize(arg.dimensionsMeta());
    if (arg.cells().size() != cellsSize) {
        throw IllegalStateException(make_string("wrong cell size, "
                                                "expected=%zu, "
                                                "actual=%zu",
                                                cellsSize,
                                                arg.cells().size()));
    }
}

void
checkDimensions(const DenseTensor &lhs, const DenseTensor &rhs,
                vespalib::stringref operation)
{
    if (lhs.dimensionsMeta() != rhs.dimensionsMeta()) {
        throw IllegalStateException(make_string("mismatching dimensions meta for "
                                                "dense tensor %s, "
                                                "lhs dimensions meta = '%s', "
                                                "rhs dimensions meta = '%s'",
                                                operation.c_str(),
                                                dimensionsMetaAsString(lhs.dimensionsMeta()).c_str(),
                                                dimensionsMetaAsString(rhs.dimensionsMeta()).c_str()));
    }
    checkCellsSize(lhs);
    checkCellsSize(rhs);
}


/*
 * Join the cells of two tensors.
 *
 * The given function is used to calculate the resulting cell value
 * for overlapping cells.
 */
template <typename Function>
Tensor::UP
joinDenseTensors(const DenseTensor &lhs, const DenseTensor &rhs,
                     Function &&func)
{
    DenseTensor::Cells cells;
    cells.reserve(lhs.cells().size());
    auto rhsCellItr = rhs.cells().cbegin();
    for (const auto &lhsCell : lhs.cells()) {
        cells.push_back(func(lhsCell, *rhsCellItr));
        ++rhsCellItr;
    }
    assert(rhsCellItr == rhs.cells().cend());
    return std::make_unique<DenseTensor>(lhs.dimensionsMeta(),
                                         std::move(cells));
}

/*
 * Join the cells of two tensors, where the rhs values are treated as negated values.
 * The given function is used to calculate the resulting cell value for overlapping cells.
 */
template <typename Function>
Tensor::UP
joinDenseTensorsNegated(const DenseTensor &lhs,
                        const DenseTensor &rhs,
                        Function &&func)
{
    DenseTensor::Cells cells;
    cells.reserve(lhs.cells().size());
    auto rhsCellItr = rhs.cells().cbegin();
    for (const auto &lhsCell : lhs.cells()) {
        cells.push_back(func(lhsCell, - *rhsCellItr));
        ++rhsCellItr;
    }
    assert(rhsCellItr == rhs.cells().cend());
    return std::make_unique<DenseTensor>(lhs.dimensionsMeta(),
                                         std::move(cells));
}


}


void
DenseTensor::CellsIterator::next()
{
    ++_cellIdx;
    if (valid()) {
        for (int64_t i = (_address.size() - 1); i >= 0; --i) {
            _address[i] = (_address[i] + 1) % _dimensionsMeta[i].size();
            if (_address[i] != 0) {
                // Outer dimension labels can only be increased when this label wraps around.
                break;
            }
        }
    }
}

DenseTensor::DenseTensor()
    : _dimensionsMeta(),
      _cells(1)
{
}

DenseTensor::DenseTensor(const DimensionsMeta &dimensionsMeta_in,
                         const Cells &cells_in)
    : _dimensionsMeta(dimensionsMeta_in),
      _cells(cells_in)
{
    checkCellsSize(*this);
}


DenseTensor::DenseTensor(const DimensionsMeta &dimensionsMeta_in,
                         Cells &&cells_in)
    : _dimensionsMeta(dimensionsMeta_in),
      _cells(std::move(cells_in))
{
    checkCellsSize(*this);
}

DenseTensor::DenseTensor(DimensionsMeta &&dimensionsMeta_in,
                         Cells &&cells_in)
    : _dimensionsMeta(std::move(dimensionsMeta_in)),
      _cells(std::move(cells_in))
{
    checkCellsSize(*this);
}

bool
DenseTensor::operator==(const DenseTensor &rhs) const
{
    return (_dimensionsMeta == rhs._dimensionsMeta) &&
            (_cells == rhs._cells);
}

eval::ValueType
DenseTensor::getType() const
{
    if (_dimensionsMeta.empty()) {
        return eval::ValueType::double_type();
    }
    std::vector<eval::ValueType::Dimension> dimensions;
    dimensions.reserve(_dimensionsMeta.size());
    for (const auto &dimensionMeta : _dimensionsMeta) {
        dimensions.emplace_back(dimensionMeta.dimension(),
                                dimensionMeta.size());
    }
    return eval::ValueType::tensor_type(dimensions);
}

double
DenseTensor::sum() const
{
    double result = 0.0;
    for (const auto &cell : _cells) {
        result += cell;
    }
    return result;
}

Tensor::UP
DenseTensor::add(const Tensor &arg) const
{
    const DenseTensor *rhs = dynamic_cast<const DenseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    checkDimensions(*this, *rhs, "add");
    return joinDenseTensors(*this, *rhs,
                            [](double lhsValue, double rhsValue)
                            { return lhsValue + rhsValue; });
}

Tensor::UP
DenseTensor::subtract(const Tensor &arg) const
{
    const DenseTensor *rhs = dynamic_cast<const DenseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    // Note that - *rhsCellItr is passed to the lambda function, that is why we do addition.
    checkDimensions(*this, *rhs, "subtract");
    return joinDenseTensorsNegated(*this, *rhs,
                                   [](double lhsValue, double rhsValue)
                                   { return lhsValue + rhsValue; });
}

Tensor::UP
DenseTensor::multiply(const Tensor &arg) const
{
    const DenseTensor *rhs = dynamic_cast<const DenseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return DenseTensorProduct(*this, *rhs).result();
}

Tensor::UP
DenseTensor::min(const Tensor &arg) const
{
    const DenseTensor *rhs = dynamic_cast<const DenseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    checkDimensions(*this, *rhs, "min");
    return joinDenseTensors(*this, *rhs,
                            [](double lhsValue, double rhsValue)
                            { return std::min(lhsValue, rhsValue); });
}

Tensor::UP
DenseTensor::max(const Tensor &arg) const
{
    const DenseTensor *rhs = dynamic_cast<const DenseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    checkDimensions(*this, *rhs, "max");
    return joinDenseTensors(*this, *rhs,
                            [](double lhsValue, double rhsValue)
                            { return std::max(lhsValue,rhsValue); });
}

Tensor::UP
DenseTensor::match(const Tensor &arg) const
{
    const DenseTensor *rhs = dynamic_cast<const DenseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    checkDimensions(*this, *rhs, "match");
    return joinDenseTensors(*this, *rhs,
                            [](double lhsValue, double rhsValue)
                            { return (lhsValue * rhsValue); });
}

Tensor::UP
DenseTensor::apply(const CellFunction &func) const
{
    Cells newCells(_cells.size());
    auto itr = newCells.begin();
    for (const auto &cell : _cells) {
        *itr = func.apply(cell);
        ++itr;
    }
    assert(itr == newCells.end());
    return std::make_unique<DenseTensor>(_dimensionsMeta,
                                         std::move(newCells));
}

Tensor::UP
DenseTensor::sum(const vespalib::string &dimension) const
{
    return DenseTensorDimensionSum(*this, dimension).result();
}

bool
DenseTensor::equals(const Tensor &arg) const
{
    const DenseTensor *rhs = dynamic_cast<const DenseTensor *>(&arg);
    if (!rhs) {
        return false;
    }
    return *this == *rhs;
}

vespalib::string
DenseTensor::toString() const
{
    std::ostringstream stream;
    stream << *this;
    return stream.str();
}

Tensor::UP
DenseTensor::clone() const
{
    return std::make_unique<DenseTensor>(_dimensionsMeta, _cells);
}

void
DenseTensor::print(std::ostream &out) const
{
    // TODO (geirst): print on common format.
    out << "[ ";
    bool first = true;
    for (const auto &dimMeta : _dimensionsMeta) {
        if (!first) {
            out << ", ";
        }
        out << dimMeta;
        first = false;
    }
    out << " ] { ";
    first = true;
    for (const auto &cell : cells()) {
        if (!first) {
            out << ", ";
        }
        out << cell;
        first = false;
    }
    out << " }";
}

void
DenseTensor::accept(TensorVisitor &visitor) const
{
    DenseTensor::CellsIterator iterator(_dimensionsMeta, _cells);
    TensorAddressBuilder addressBuilder;
    TensorAddress address;
    vespalib::string label;
    while (iterator.valid()) {
        addressBuilder.clear();
        auto rawIndex = iterator.address().begin();
        for (const auto &dimension : _dimensionsMeta) {
            label = vespalib::make_string("%zu", *rawIndex);
            addressBuilder.add(dimension.dimension(), label);
            ++rawIndex;
        }
        address = addressBuilder.build();
        visitor.visit(address, iterator.cell());
        iterator.next();
    }
}

std::ostream &
operator<<(std::ostream &out, const DenseTensor::DimensionMeta &value)
{
    out << value.dimension() << ":" << value.size();
    return out;
}

} // namespace vespalib::tensor
} // namespace vespalib
