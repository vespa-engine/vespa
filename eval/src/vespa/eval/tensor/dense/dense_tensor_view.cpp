// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_view.h"
#include "dense_tensor_apply.hpp"
#include "dense_tensor_reduce.hpp"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/eval/tensor/tensor_address_builder.h>
#include <vespa/eval/tensor/tensor_visitor.h>
#include <vespa/eval/eval/operation.h>
#include <sstream>

using vespalib::eval::TensorSpec;

namespace vespalib::tensor {

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
checkCellsSize(const DenseTensorView &arg)
{
    auto cellsSize = calcCellsSize(arg.fast_type());
    if (arg.cellsRef().size() != cellsSize) {
        throw IllegalStateException(make_string("wrong cell size, "
                                                "expected=%zu, "
                                                "actual=%zu",
                                                cellsSize,
                                                arg.cellsRef().size()));
    }
}

void
checkDimensions(const DenseTensorView &lhs, const DenseTensorView &rhs,
                vespalib::stringref operation)
{
    if (lhs.fast_type() != rhs.fast_type()) {
        throw IllegalStateException(make_string("mismatching dimensions for "
                                                "dense tensor %s, "
                                                "lhs dimensions = '%s', "
                                                "rhs dimensions = '%s'",
                                                operation.c_str(),
                                                dimensionsAsString(lhs.fast_type()).c_str(),
                                                dimensionsAsString(rhs.fast_type()).c_str()));
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
joinDenseTensors(const DenseTensorView &lhs, const DenseTensorView &rhs,
                 Function &&func)
{
    DenseTensor::Cells cells;
    cells.reserve(lhs.cellsRef().size());
    auto rhsCellItr = rhs.cellsRef().cbegin();
    for (const auto &lhsCell : lhs.cellsRef()) {
        cells.push_back(func(lhsCell, *rhsCellItr));
        ++rhsCellItr;
    }
    assert(rhsCellItr == rhs.cellsRef().cend());
    return std::make_unique<DenseTensor>(lhs.fast_type(),
                                         std::move(cells));
}


template <typename Function>
Tensor::UP
joinDenseTensors(const DenseTensorView &lhs, const Tensor &rhs,
                 vespalib::stringref operation,
                 Function &&func)
{
    const DenseTensorView *view = dynamic_cast<const DenseTensorView *>(&rhs);
    if (view) {
        checkDimensions(lhs, *view, operation);
        return joinDenseTensors(lhs, *view, func);
    }
    return Tensor::UP();
}

bool sameCells(DenseTensorView::CellsRef lhs, DenseTensorView::CellsRef rhs)
{
    if (lhs.size() != rhs.size()) {
        return false;
    }
    for (size_t i = 0; i < lhs.size(); ++i) {
        if (lhs[i] != rhs[i]) {
            return false;
        }
    }
    return true;
}

}


DenseTensorView::DenseTensorView(const DenseTensor &rhs)
    : _typeRef(rhs.fast_type()),
      _cellsRef(rhs.cellsRef())
{
}


bool
DenseTensorView::operator==(const DenseTensorView &rhs) const
{
    return (_typeRef == rhs._typeRef) && sameCells(_cellsRef, rhs._cellsRef);
}

const eval::ValueType &
DenseTensorView::type() const
{
    return _typeRef;
}

double
DenseTensorView::as_double() const
{
    double result = 0.0;
    for (const auto &cell : _cellsRef) {
        result += cell;
    }
    return result;
}

Tensor::UP
DenseTensorView::apply(const CellFunction &func) const
{
    Cells newCells(_cellsRef.size());
    auto itr = newCells.begin();
    for (const auto &cell : _cellsRef) {
        *itr = func.apply(cell);
        ++itr;
    }
    assert(itr == newCells.end());
    return std::make_unique<DenseTensor>(_typeRef, std::move(newCells));
}

bool
DenseTensorView::equals(const Tensor &arg) const
{
    const DenseTensorView *view = dynamic_cast<const DenseTensorView *>(&arg);
    if (view) {
        return *this == *view;
    }
    return false;
}

Tensor::UP
DenseTensorView::clone() const
{
    return std::make_unique<DenseTensor>(_typeRef,
                                         Cells(_cellsRef.cbegin(), _cellsRef.cend()));
}

namespace {

void
buildAddress(const DenseTensorCellsIterator &itr, TensorSpec::Address &address)
{
    auto addressItr = itr.address().begin();
    for (const auto &dim : itr.fast_type().dimensions()) {
        address.emplace(std::make_pair(dim.name, TensorSpec::Label(*addressItr++)));
    }
    assert(addressItr == itr.address().end());
}

}

TensorSpec
DenseTensorView::toSpec() const
{
    TensorSpec result(type().to_spec());
    TensorSpec::Address address;
    for (CellsIterator itr(_typeRef, _cellsRef); itr.valid(); itr.next()) {
        buildAddress(itr, address);
        result.add(address, itr.cell());
        address.clear();
    }
    return result;
}

void
DenseTensorView::accept(TensorVisitor &visitor) const
{
    CellsIterator iterator(_typeRef, _cellsRef);
    TensorAddressBuilder addressBuilder;
    TensorAddress address;
    vespalib::string label;
    while (iterator.valid()) {
        addressBuilder.clear();
        auto rawIndex = iterator.address().begin();
        for (const auto &dimension : _typeRef.dimensions()) {
            label = vespalib::make_string("%u", *rawIndex);
            addressBuilder.add(dimension.name, label);
            ++rawIndex;
        }
        address = addressBuilder.build();
        visitor.visit(address, iterator.cell());
        iterator.next();
    }
}

Tensor::UP
DenseTensorView::join(join_fun_t function, const Tensor &arg) const
{
    if (fast_type() == arg.type()) {
        if (function == eval::operation::Mul::f) {
            return joinDenseTensors(*this, arg, "mul",
                                    [](double a, double b) { return (a * b); });
        }
        if (function == eval::operation::Add::f) {
            return joinDenseTensors(*this, arg, "add",
                                    [](double a, double b) { return (a + b); });
        }
        return joinDenseTensors(*this, arg, "join", function);
    }
    if (function == eval::operation::Mul::f) {
        return dense::apply(*this, arg, [](double a, double b) { return (a * b); });
    }
    if (function == eval::operation::Add::f) {
        return dense::apply(*this, arg, [](double a, double b) { return (a + b); });
    }
    return dense::apply(*this, arg, function);
}

Tensor::UP
DenseTensorView::reduce(join_fun_t op, const std::vector<vespalib::string> &dimensions) const
{
    const std::vector<vespalib::string> & dims = (dimensions.empty() ? _typeRef.dimension_names() : dimensions);
    if (op == eval::operation::Mul::f) {
        return dense::reduce(*this, dims, [](double a, double b) { return (a * b);});
    }
    if (op == eval::operation::Add::f) {
        return dense::reduce(*this, dims, [](double a, double b) { return (a + b);});
    }
    return dense::reduce(*this, dims, op);
}

}
