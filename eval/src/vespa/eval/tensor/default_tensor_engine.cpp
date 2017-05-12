// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "default_tensor_engine.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/operation_visitor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include "tensor.h"
#include "serialization/typed_binary_format.h"
#include "dense/dense_tensor.h"
#include "dense/dense_tensor_builder.h"
#include "dense/dense_tensor_function_compiler.h"
#include "default_tensor.h"
#include "wrapped_simple_tensor.h"

namespace vespalib {
namespace tensor {

using Value = eval::Value;
using ValueType = eval::ValueType;
using ErrorValue = eval::ErrorValue;
using DoubleValue = eval::DoubleValue;
using TensorValue = eval::TensorValue;
using TensorSpec = eval::TensorSpec;

namespace {

const eval::TensorEngine &simple_engine() { return eval::SimpleTensorEngine::ref(); }
const eval::TensorEngine &default_engine() { return DefaultTensorEngine::ref(); }

// map tensors to simple tensors before fall-back evaluation

const eval::SimpleTensor &to_simple(const eval::Tensor &tensor, Stash &stash) {
    if (auto wrapped = dynamic_cast<const WrappedSimpleTensor *>(&tensor)) {
        return wrapped->get();
    }
    TensorSpec spec = tensor.engine().to_spec(tensor);
    using PTR = std::unique_ptr<eval::SimpleTensor>;
    return *stash.create<PTR>(eval::SimpleTensor::create(spec));
}

const Value &to_simple(const Value &value, Stash &stash) {
    if (auto tensor = value.as_tensor()) {
        return stash.create<TensorValue>(to_simple(*tensor, stash));
    }
    return value;
}

// map tensors to default tensors after fall-back evaluation

const Value &to_default(const Value &value, Stash &stash) {
    if (auto tensor = value.as_tensor()) {
        if (auto simple = dynamic_cast<const eval::SimpleTensor *>(tensor)) {
            if (!Tensor::supported({simple->type()})) {
                return stash.create<TensorValue>(std::make_unique<WrappedSimpleTensor>(*simple));
            }
        }
        TensorSpec spec = tensor->engine().to_spec(*tensor);
        return stash.create<TensorValue>(default_engine().create(spec));
    }
    return value;
}

const Value &to_value(std::unique_ptr<Tensor> tensor, Stash &stash) {
    if (tensor->getType().is_tensor()) {
        return stash.create<TensorValue>(std::move(tensor));
    }
    return stash.create<DoubleValue>(tensor->sum());
}

} // namespace vespalib::tensor::<unnamed>

const DefaultTensorEngine DefaultTensorEngine::_engine;

eval::ValueType
DefaultTensorEngine::type_of(const Tensor &tensor) const
{
    assert(&tensor.engine() == this);
    const tensor::Tensor &my_tensor = static_cast<const tensor::Tensor &>(tensor);
    return my_tensor.getType();
}

bool
DefaultTensorEngine::equal(const Tensor &a, const Tensor &b) const
{
    assert(&a.engine() == this);
    assert(&b.engine() == this);
    const tensor::Tensor &my_a = static_cast<const tensor::Tensor &>(a);
    const tensor::Tensor &my_b = static_cast<const tensor::Tensor &>(b);
    return my_a.equals(my_b);
}

vespalib::string
DefaultTensorEngine::to_string(const Tensor &tensor) const
{
    assert(&tensor.engine() == this);
    const tensor::Tensor &my_tensor = static_cast<const tensor::Tensor &>(tensor);
    return my_tensor.toString();
}

TensorSpec
DefaultTensorEngine::to_spec(const Tensor &tensor) const
{
    assert(&tensor.engine() == this);
    const tensor::Tensor &my_tensor = static_cast<const tensor::Tensor &>(tensor);
    return my_tensor.toSpec();
}

eval::TensorFunction::UP
DefaultTensorEngine::compile(eval::tensor_function::Node_UP expr) const
{
    return DenseTensorFunctionCompiler::compile(std::move(expr));
}

struct IsAddOperation : public eval::DefaultOperationVisitor {
    bool result = false;
    void visitDefault(const eval::Operation &) override {}
    void visit(const eval::operation::Add &) override { result = true; }
};

std::unique_ptr<eval::Tensor>
DefaultTensorEngine::create(const TensorSpec &spec) const
{
    ValueType type = ValueType::from_spec(spec.type());
    bool is_dense = false;
    bool is_sparse = false;
    for (const auto &dimension: type.dimensions()) {
        if (dimension.is_mapped()) {
            is_sparse = true;
        }
        if (dimension.is_indexed()) {
            is_dense = true;
        }
    }
    if (is_dense && is_sparse) {
        return std::make_unique<WrappedSimpleTensor>(eval::SimpleTensor::create(spec));
    } else if (is_dense) {
        DenseTensorBuilder builder;
        std::map<vespalib::string,DenseTensorBuilder::Dimension> dimension_map;
        for (const auto &dimension: type.dimensions()) {
            dimension_map[dimension.name] = builder.defineDimension(dimension.name, dimension.size);
        }
        for (const auto &cell: spec.cells()) {
            const auto &address = cell.first;
            for (const auto &binding: address) {
                builder.addLabel(dimension_map[binding.first], binding.second.index);
            }
            builder.addCell(cell.second);
        }
        return builder.build();
    } else { // sparse
        DefaultTensor::builder builder;
        std::map<vespalib::string,DefaultTensor::builder::Dimension> dimension_map;
        for (const auto &dimension: type.dimensions()) {
            dimension_map[dimension.name] = builder.define_dimension(dimension.name);
        }
        for (const auto &cell: spec.cells()) {
            const auto &address = cell.first;
            for (const auto &binding: address) {
                builder.add_label(dimension_map[binding.first], binding.second.name);
            }
            builder.add_cell(cell.second);
        }
        return builder.build();
    }
}

const eval::Value &
DefaultTensorEngine::reduce(const Tensor &tensor, const BinaryOperation &op, const std::vector<vespalib::string> &dimensions, Stash &stash) const
{
    assert(&tensor.engine() == this);
    const tensor::Tensor &my_tensor = static_cast<const tensor::Tensor &>(tensor);
    if (!tensor::Tensor::supported({my_tensor.getType()})) {
        return to_default(simple_engine().reduce(to_simple(my_tensor, stash), op, dimensions, stash), stash);
    }
    IsAddOperation check;
    op.accept(check);
    tensor::Tensor::UP result;
    if (check.result) {
        if (dimensions.empty()) { // sum
            return stash.create<eval::DoubleValue>(my_tensor.sum());
        } else { // dimension sum
            for (const auto &dimension: dimensions) {
                if (result) {
                    result = result->sum(dimension);
                } else {
                    result = my_tensor.sum(dimension);
                }
            }
        }
    } else {
        result = my_tensor.reduce(op, dimensions);
    }
    if (result) {
        return to_value(std::move(result), stash);
    }
    return stash.create<ErrorValue>();
}

struct CellFunctionAdapter : tensor::CellFunction {
    const eval::UnaryOperation &op;
    CellFunctionAdapter(const eval::UnaryOperation &op_in) : op(op_in) {}
    virtual double apply(double value) const override { return op.eval(value); }
};

const eval::Value &
DefaultTensorEngine::map(const UnaryOperation &op, const Tensor &a, Stash &stash) const
{
    assert(&a.engine() == this);
    const tensor::Tensor &my_a = static_cast<const tensor::Tensor &>(a);
    if (!tensor::Tensor::supported({my_a.getType()})) {
        return to_default(simple_engine().map(op, to_simple(my_a, stash), stash), stash);
    }
    CellFunctionAdapter cell_function(op);
    return to_value(my_a.apply(cell_function), stash);
}

struct TensorOperationOverride : eval::DefaultOperationVisitor {
    const tensor::Tensor &lhs;
    const tensor::Tensor &rhs;
    tensor::Tensor::UP result;
    TensorOperationOverride(const tensor::Tensor &lhs_in,
                            const tensor::Tensor &rhs_in)
        : lhs(lhs_in), rhs(rhs_in), result() {}
    virtual void visitDefault(const eval::Operation &op) override {
        // empty result indicates error
        const eval::BinaryOperation *binaryOp =
            dynamic_cast<const eval::BinaryOperation *>(&op);
        if (binaryOp) {
            result = lhs.apply(*binaryOp, rhs);
        }
    }
    virtual void visit(const eval::operation::Add &) override {
        result = lhs.add(rhs);
    }
    virtual void visit(const eval::operation::Sub &) override {
        result = lhs.subtract(rhs);
    }
    virtual void visit(const eval::operation::Mul &) override {
        if (lhs.getType() == rhs.getType()) {
            result = lhs.match(rhs);
        } else {
            result = lhs.multiply(rhs);
        }
    }
    virtual void visit(const eval::operation::Min &) override {
        result = lhs.min(rhs);
    }
    virtual void visit(const eval::operation::Max &) override {
        result = lhs.max(rhs);
    }
};

const eval::Value &
DefaultTensorEngine::apply(const BinaryOperation &op, const Tensor &a, const Tensor &b, Stash &stash) const
{
    assert(&a.engine() == this);
    assert(&b.engine() == this);
    const tensor::Tensor &my_a = static_cast<const tensor::Tensor &>(a);
    const tensor::Tensor &my_b = static_cast<const tensor::Tensor &>(b);
    if (!tensor::Tensor::supported({my_a.getType(), my_b.getType()})) {
        return to_default(simple_engine().apply(op, to_simple(my_a, stash), to_simple(my_b, stash), stash), stash);
    }
    TensorOperationOverride tensor_override(my_a, my_b);
    op.accept(tensor_override);
    if (tensor_override.result) {
        return to_value(std::move(tensor_override.result), stash);
    } else {
        return stash.create<ErrorValue>();
    }
}

//-----------------------------------------------------------------------------

void
DefaultTensorEngine::encode(const Value &value, nbostream &output, Stash &) const
{
    if (auto tensor = value.as_tensor()) {
        TypedBinaryFormat::serialize(output, static_cast<const tensor::Tensor &>(*tensor));
    } else {
        TypedBinaryFormat::serialize(output, DenseTensor(ValueType::double_type(), {value.as_double()}));
    }
}

const Value &
DefaultTensorEngine::decode(nbostream &input, Stash &stash) const
{
    return to_value(TypedBinaryFormat::deserialize(input), stash);
}

//-----------------------------------------------------------------------------

const Value &
DefaultTensorEngine::map(const Value &a, const std::function<double(double)> &function, Stash &stash) const
{
    return to_default(simple_engine().map(to_simple(a, stash), function, stash), stash);
}

const Value &
DefaultTensorEngine::join(const Value &a, const Value &b, const std::function<double(double,double)> &function, Stash &stash) const
{
    return to_default(simple_engine().join(to_simple(a, stash), to_simple(b, stash), function, stash), stash);
}

const Value &
DefaultTensorEngine::reduce(const Value &a, Aggr aggr, const std::vector<vespalib::string> &dimensions, Stash &stash) const
{
    return to_default(simple_engine().reduce(to_simple(a, stash), aggr, dimensions, stash), stash);
}

const Value &
DefaultTensorEngine::concat(const Value &a, const Value &b, const vespalib::string &dimension, Stash &stash) const
{
    return to_default(simple_engine().concat(to_simple(a, stash), to_simple(b, stash), dimension, stash), stash);
}

const Value &
DefaultTensorEngine::rename(const Value &a, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to, Stash &stash) const
{
    return to_default(simple_engine().rename(to_simple(a, stash), from, to, stash), stash);
}

//-----------------------------------------------------------------------------

} // namespace vespalib::tensor
} // namespace vespalib
