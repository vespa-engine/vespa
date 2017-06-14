// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "wrapped_simple_tensor.h"
#include "tensor_address_builder.h"
#include "tensor_visitor.h"
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace vespalib::tensor {

bool
WrappedSimpleTensor::equals(const Tensor &arg) const
{
    if (auto other = dynamic_cast<const WrappedSimpleTensor *>(&arg)) {
        return eval::SimpleTensor::equal(_tensor, other->_tensor);
    }
    return false;
}

vespalib::string
WrappedSimpleTensor::toString() const
{
    return eval::SimpleTensorEngine::ref().to_string(_tensor);
}

eval::TensorSpec
WrappedSimpleTensor::toSpec() const
{
    return eval::SimpleTensorEngine::ref().to_spec(_tensor);
}

double
WrappedSimpleTensor::sum() const
{
    double result = 0.0;
    for (const auto &cell: _tensor.cells()) {
        result += cell.value;
    }
    return result;
}

void
WrappedSimpleTensor::accept(TensorVisitor &visitor) const
{
    TensorAddressBuilder addr;
    const auto &dimensions = _tensor.type().dimensions();
    for (const auto &cell: _tensor.cells()) {
        addr.clear();
        for (size_t i = 0; i < dimensions.size(); ++i) {
            if (dimensions[i].is_indexed()) {
                addr.add(dimensions[i].name, make_string("%zu", cell.address[i].index));
            } else {
                addr.add(dimensions[i].name, cell.address[i].name);
            }
        }
        visitor.visit(addr.build(), cell.value);
    }
}

void
WrappedSimpleTensor::print(std::ostream &out) const
{
    out << toString();
}

Tensor::UP
WrappedSimpleTensor::clone() const
{
    auto tensor = std::make_unique<eval::SimpleTensor>(_tensor.type(), _tensor.cells());
    return std::make_unique<WrappedSimpleTensor>(std::move(tensor));
}

//-----------------------------------------------------------------------------

Tensor::UP
WrappedSimpleTensor::add(const Tensor &) const
{
    abort();
    return Tensor::UP();
}

Tensor::UP
WrappedSimpleTensor::subtract(const Tensor &) const
{
    abort();
    return Tensor::UP();
}

Tensor::UP
WrappedSimpleTensor::multiply(const Tensor &) const
{
    abort();
    return Tensor::UP();
}

Tensor::UP
WrappedSimpleTensor::min(const Tensor &) const
{
    abort();
    return Tensor::UP();
}

Tensor::UP
WrappedSimpleTensor::max(const Tensor &) const
{
    abort();
    return Tensor::UP();
}

Tensor::UP
WrappedSimpleTensor::match(const Tensor &) const
{
    abort();
    return Tensor::UP();
}

Tensor::UP
WrappedSimpleTensor::apply(const CellFunction &) const
{
    abort();
    return Tensor::UP();
}

Tensor::UP
WrappedSimpleTensor::sum(const vespalib::string &) const
{
    abort();
    return Tensor::UP();
}

Tensor::UP
WrappedSimpleTensor::apply(const eval::BinaryOperation &, const Tensor &) const
{
    abort();
    return Tensor::UP();
}

Tensor::UP
WrappedSimpleTensor::reduce(const eval::BinaryOperation &, const std::vector<vespalib::string> &) const
{
    abort();
    return Tensor::UP();
}

} // namespace vespalib::tensor
