// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_tensor.h"
#include "dense_tensor_view.h"
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
    return DenseTensorView(*this).sum();
}

Tensor::UP
DenseTensor::add(const Tensor &arg) const
{
    return DenseTensorView(*this).add(arg);
}

Tensor::UP
DenseTensor::subtract(const Tensor &arg) const
{
    return DenseTensorView(*this).subtract(arg);
}

Tensor::UP
DenseTensor::multiply(const Tensor &arg) const
{
    return DenseTensorView(*this).multiply(arg);
}

Tensor::UP
DenseTensor::min(const Tensor &arg) const
{
    return DenseTensorView(*this).min(arg);
}

Tensor::UP
DenseTensor::max(const Tensor &arg) const
{
    return DenseTensorView(*this).max(arg);
}

Tensor::UP
DenseTensor::match(const Tensor &arg) const
{
    return DenseTensorView(*this).match(arg);
}

Tensor::UP
DenseTensor::apply(const CellFunction &func) const
{
    return DenseTensorView(*this).apply(func);
}

Tensor::UP
DenseTensor::sum(const vespalib::string &dimension) const
{
    return DenseTensorView(*this).sum(dimension);
}

bool
DenseTensor::equals(const Tensor &arg) const
{
    return DenseTensorView(*this).equals(arg);
}

vespalib::string
DenseTensor::toString() const
{
    return DenseTensorView(*this).toString();
}

Tensor::UP
DenseTensor::clone() const
{
    return DenseTensorView(*this).clone();
}

TensorSpec
DenseTensor::toSpec() const
{
    return DenseTensorView(*this).toSpec();
}

void
DenseTensor::print(std::ostream &out) const
{
    return DenseTensorView(*this).print(out);
}

void
DenseTensor::accept(TensorVisitor &visitor) const
{
    return DenseTensorView(*this).accept(visitor);
}

Tensor::UP
DenseTensor::apply(const eval::BinaryOperation &op, const Tensor &arg) const
{
    return DenseTensorView(*this).apply(op, arg);
}

Tensor::UP
DenseTensor::reduce(const eval::BinaryOperation &op,
                    const std::vector<vespalib::string> &dimensions) const
{
    return DenseTensorView(*this).reduce(op, dimensions);
}

} // namespace vespalib::tensor
} // namespace vespalib
