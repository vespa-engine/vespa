// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "wrapped_simple_tensor.h"
#include "tensor_address_builder.h"
#include "tensor_visitor.h"
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.tensor.wrapped_simple_tensor");

namespace vespalib::tensor {

bool
WrappedSimpleTensor::equals(const Tensor &arg) const
{
    auto lhs_spec = _tensor.engine().to_spec(_tensor);
    auto rhs_spec = arg.engine().to_spec(arg);
    return (lhs_spec == rhs_spec);
}

eval::TensorSpec
WrappedSimpleTensor::toSpec() const
{
    return eval::SimpleTensorEngine::ref().to_spec(_tensor);
}

double
WrappedSimpleTensor::as_double() const
{
    return _tensor.as_double();
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

Tensor::UP
WrappedSimpleTensor::clone() const
{
    auto tensor = std::make_unique<eval::SimpleTensor>(_tensor.type(), _tensor.cells());
    return std::make_unique<WrappedSimpleTensor>(std::move(tensor));
}

//-----------------------------------------------------------------------------

Tensor::UP
WrappedSimpleTensor::apply(const CellFunction &) const
{
    LOG_ABORT("should not be reached");
    return Tensor::UP();
}

Tensor::UP
WrappedSimpleTensor::join(join_fun_t, const Tensor &) const
{
    LOG_ABORT("should not be reached");
    return Tensor::UP();
}

Tensor::UP
WrappedSimpleTensor::reduce(join_fun_t, const std::vector<vespalib::string> &) const
{
    LOG_ABORT("should not be reached");
    return Tensor::UP();
}

} // namespace vespalib::tensor
