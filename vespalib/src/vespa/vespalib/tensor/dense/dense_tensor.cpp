// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_tensor.h"
#include "dense_tensor_apply.hpp"
#include "dense_tensor_reduce.hpp"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/tensor/tensor_address_builder.h>
#include <vespa/vespalib/tensor/tensor_visitor.h>
#include <vespa/vespalib/eval/operation.h>
#include <sstream>

using vespalib::eval::TensorSpec;

namespace vespalib {
namespace tensor {

namespace {

string
dimensionsAsString(const eval::ValueType &type)
{
    std::ostringstream oss;
    bool first = true;
    oss << "[";
    for (const auto &dim : type.dimensions()) {
        if (!first) {
            oss << ",";
        }
        first = false;
        oss << dim.name << ":" << dim.size;
    }
    oss << "]";
    return oss.str();
}

size_t
calcCellsSize(const eval::ValueType &type)
{
    size_t cellsSize = 1;
    for (const auto &dim : type.dimensions()) {
        cellsSize *= dim.size;
    }
    return cellsSize;
}


void
checkCellsSize(const DenseTensor &arg)
{
    auto cellsSize = calcCellsSize(arg.type());
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
    if (lhs.type() != rhs.type()) {
        throw IllegalStateException(make_string("mismatching dimensions for "
                                                "dense tensor %s, "
                                                "lhs dimensions = '%s', "
                                                "rhs dimensions = '%s'",
                                                operation.c_str(),
                                                dimensionsAsString(lhs.type()).c_str(),
                                                dimensionsAsString(rhs.type()).c_str()));
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
    return std::make_unique<DenseTensor>(lhs.type(),
                                         std::move(cells));
}

}


void
DenseTensor::CellsIterator::next()
{
    ++_cellIdx;
    if (valid()) {
        for (int64_t i = (_address.size() - 1); i >= 0; --i) {
            _address[i] = (_address[i] + 1) % _type.dimensions()[i].size;
            if (_address[i] != 0) {
                // Outer dimension labels can only be increased when this label wraps around.
                break;
            }
        }
    }
}

DenseTensor::DenseTensor()
    : _type(eval::ValueType::double_type()),
      _cells(1)
{
}

DenseTensor::DenseTensor(const eval::ValueType &type_in,
                         const Cells &cells_in)
    : _type(type_in),
      _cells(cells_in)
{
    checkCellsSize(*this);
}


DenseTensor::DenseTensor(const eval::ValueType &type_in,
                         Cells &&cells_in)
    : _type(type_in),
      _cells(std::move(cells_in))
{
    checkCellsSize(*this);
}

DenseTensor::DenseTensor(eval::ValueType &&type_in,
                         Cells &&cells_in)
    : _type(std::move(type_in)),
      _cells(std::move(cells_in))
{
    checkCellsSize(*this);
}

bool
DenseTensor::operator==(const DenseTensor &rhs) const
{
    return (_type == rhs._type) &&
            (_cells == rhs._cells);
}

eval::ValueType
DenseTensor::getType() const
{
    return _type;
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
    return dense::apply(*this, *rhs,
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
    return dense::apply(*this, *rhs,
                        [](double lhsValue, double rhsValue)
                        { return lhsValue - rhsValue; });
}

Tensor::UP
DenseTensor::multiply(const Tensor &arg) const
{
    const DenseTensor *rhs = dynamic_cast<const DenseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return dense::apply(*this, *rhs, [](double lhsValue, double rhsValue)
    { return lhsValue * rhsValue; });
}

Tensor::UP
DenseTensor::min(const Tensor &arg) const
{
    const DenseTensor *rhs = dynamic_cast<const DenseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return dense::apply(*this, *rhs,
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
    return dense::apply(*this, *rhs,
                        [](double lhsValue, double rhsValue)
                        { return std::max(lhsValue, rhsValue); });
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
    return std::make_unique<DenseTensor>(_type,
                                         std::move(newCells));
}

Tensor::UP
DenseTensor::sum(const vespalib::string &dimension) const
{
    return dense::reduce(*this, { dimension },
                          [](double lhsValue, double rhsValue)
                          { return lhsValue + rhsValue; });
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
    return std::make_unique<DenseTensor>(_type, _cells);
}

namespace {

void
buildAddress(const DenseTensor::CellsIterator &itr, TensorSpec::Address &address)
{
    auto addressItr = itr.address().begin();
    for (const auto &dim : itr.type().dimensions()) {
        address.emplace(std::make_pair(dim.name, TensorSpec::Label(*addressItr++)));
    }
    assert(addressItr == itr.address().end());
}

}

TensorSpec
DenseTensor::toSpec() const
{
    TensorSpec result(getType().to_spec());
    TensorSpec::Address address;
    for (CellsIterator itr(_type, _cells); itr.valid(); itr.next()) {
        buildAddress(itr, address);
        result.add(address, itr.cell());
        address.clear();
    }
    return result;
}

void
DenseTensor::print(std::ostream &out) const
{
    // TODO (geirst): print on common format.
    out << "[ ";
    bool first = true;
    for (const auto &dim : _type.dimensions()) {
        if (!first) {
            out << ", ";
        }
        out << dim.name << ":" << dim.size;
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
    DenseTensor::CellsIterator iterator(_type, _cells);
    TensorAddressBuilder addressBuilder;
    TensorAddress address;
    vespalib::string label;
    while (iterator.valid()) {
        addressBuilder.clear();
        auto rawIndex = iterator.address().begin();
        for (const auto &dimension : _type.dimensions()) {
            label = vespalib::make_string("%zu", *rawIndex);
            addressBuilder.add(dimension.name, label);
            ++rawIndex;
        }
        address = addressBuilder.build();
        visitor.visit(address, iterator.cell());
        iterator.next();
    }
}

Tensor::UP
DenseTensor::apply(const eval::BinaryOperation &op, const Tensor &arg) const
{
    const DenseTensor *rhs = dynamic_cast<const DenseTensor *>(&arg);
    if (!rhs) {
        return Tensor::UP();
    }
    return dense::apply(*this, *rhs,
                        [&op](double lhsValue, double rhsValue)
                        { return op.eval(lhsValue, rhsValue); });
}

Tensor::UP
DenseTensor::reduce(const eval::BinaryOperation &op,
                    const std::vector<vespalib::string> &dimensions) const
{
    return dense::reduce(*this,
                         (dimensions.empty() ? _type.dimension_names() : dimensions),
                         [&op](double lhsValue, double rhsValue)
                         { return op.eval(lhsValue, rhsValue); });
}

} // namespace vespalib::tensor
} // namespace vespalib
