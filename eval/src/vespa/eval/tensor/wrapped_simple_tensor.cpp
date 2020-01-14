// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cell_values.h"
#include "tensor_address_builder.h"
#include "tensor_visitor.h"
#include "wrapped_simple_tensor.h"
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.tensor.wrapped_simple_tensor");

namespace vespalib::tensor {

using eval::SimpleTensor;
using eval::TensorSpec;

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
}

Tensor::UP
WrappedSimpleTensor::join(join_fun_t, const Tensor &) const
{
    LOG_ABORT("should not be reached");
}

Tensor::UP
WrappedSimpleTensor::merge(join_fun_t, const Tensor &) const
{
    LOG_ABORT("should not be reached");
}

Tensor::UP
WrappedSimpleTensor::reduce(join_fun_t, const std::vector<vespalib::string> &) const
{
    LOG_ABORT("should not be reached");
}

namespace {

TensorSpec::Address
convertToOnlyMappedDimensions(const TensorSpec::Address &address)
{
    TensorSpec::Address result;
    for (const auto &elem : address) {
        if (elem.second.is_indexed()) {
            result.emplace(std::make_pair(elem.first,
                    TensorSpec::Label(vespalib::make_string("%zu", elem.second.index))));
        } else {
            result.emplace(elem);
        }
    }
    return result;
}

}

std::unique_ptr<Tensor>
WrappedSimpleTensor::modify(join_fun_t op, const CellValues &cellValues) const
{
    TensorSpec oldTensor = toSpec();
    TensorSpec toModify = cellValues.toSpec();
    TensorSpec result(type().to_spec());

    for (const auto &cell : oldTensor.cells()) {
        TensorSpec::Address mappedAddress = convertToOnlyMappedDimensions(cell.first);
        auto itr = toModify.cells().find(mappedAddress);
        if (itr != toModify.cells().end()) {
            result.add(cell.first, op(cell.second, itr->second));
        } else {
            result.add(cell.first, cell.second);
        }
    }
    return std::make_unique<WrappedSimpleTensor>(SimpleTensor::create(result));
}

std::unique_ptr<Tensor>
WrappedSimpleTensor::add(const Tensor &arg) const
{
    const auto *rhs = dynamic_cast<const WrappedSimpleTensor *>(&arg);
    if (!rhs || type() != rhs->type()) {
        return Tensor::UP();
    }

    TensorSpec oldTensor = toSpec();
    TensorSpec argTensor = rhs->toSpec();
    TensorSpec result(type().to_spec());
    for (const auto &cell : oldTensor.cells()) {
        auto argItr = argTensor.cells().find(cell.first);
        if (argItr != argTensor.cells().end()) {
            result.add(argItr->first, argItr->second);
        } else {
            result.add(cell.first, cell.second);
        }
    }
    for (const auto &cell : argTensor.cells()) {
        auto resultItr = result.cells().find(cell.first);
        if (resultItr == result.cells().end()) {
            result.add(cell.first, cell.second);
        }
    }
    return std::make_unique<WrappedSimpleTensor>(SimpleTensor::create(result));
}

namespace {

TensorSpec::Address
extractMappedDimensions(const TensorSpec::Address &address)
{
    TensorSpec::Address result;
    for (const auto &elem : address) {
        if (elem.second.is_mapped()) {
            result.emplace(elem);
        }
    }
    return result;
}

}

std::unique_ptr<Tensor>
WrappedSimpleTensor::remove(const CellValues &cellAddresses) const
{
    TensorSpec oldTensor = toSpec();
    TensorSpec toRemove = cellAddresses.toSpec();
    TensorSpec result(type().to_spec());

    for (const auto &cell : oldTensor.cells()) {
        TensorSpec::Address mappedAddress = extractMappedDimensions(cell.first);
        auto itr = toRemove.cells().find(mappedAddress);
        if (itr == toRemove.cells().end()) {
            result.add(cell.first, cell.second);
        }
    }
    return std::make_unique<WrappedSimpleTensor>(SimpleTensor::create(result));
}

}
