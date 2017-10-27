// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "default_tensor_engine.h"
#include "tensor.h"
#include "default_tensor.h"
#include "wrapped_simple_tensor.h"
#include "serialization/typed_binary_format.h"
#include "dense/dense_tensor.h"
#include "dense/dense_tensor_builder.h"
#include "dense/dense_tensor_function_compiler.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <cassert>


namespace vespalib {
namespace tensor {

using eval::Aggr;
using eval::Aggregator;
using eval::DoubleValue;
using eval::ErrorValue;
using eval::TensorSpec;
using eval::TensorValue;
using eval::Value;
using eval::ValueType;

using map_fun_t = eval::TensorEngine::map_fun_t;
using join_fun_t = eval::TensorEngine::join_fun_t;

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
    if (!tensor) {
        return ErrorValue::instance;
    }
    if (tensor->getType().is_tensor()) {
        return stash.create<TensorValue>(std::move(tensor));
    }
    return stash.create<DoubleValue>(tensor->sum());
}

const Value &fallback_join(const Value &a, const Value &b, join_fun_t function, Stash &stash) {
    return to_default(simple_engine().join(to_simple(a, stash), to_simple(b, stash), function, stash), stash);
}

const Value &fallback_reduce(const Value &a, eval::Aggr aggr, const std::vector<vespalib::string> &dimensions, Stash &stash) {
    return to_default(simple_engine().reduce(to_simple(a, stash), aggr, dimensions, stash), stash);
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

struct CellFunctionFunAdapter : tensor::CellFunction {
    map_fun_t fun;
    CellFunctionFunAdapter(map_fun_t fun_in) : fun(fun_in) {}
    virtual double apply(double value) const override { return fun(value); }
};

struct CellFunctionBindLeftAdapter : tensor::CellFunction {
    join_fun_t fun;
    double a;
    CellFunctionBindLeftAdapter(join_fun_t fun_in, double bound) : fun(fun_in), a(bound) {}
    virtual double apply(double b) const override { return fun(a, b); }
};

struct CellFunctionBindRightAdapter : tensor::CellFunction {
    join_fun_t fun;
    double b;
    CellFunctionBindRightAdapter(join_fun_t fun_in, double bound) : fun(fun_in), b(bound) {}
    virtual double apply(double a) const override { return fun(a, b); }
};

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
DefaultTensorEngine::map(const Value &a, map_fun_t function, Stash &stash) const
{
    if (a.is_double()) {
        return stash.create<DoubleValue>(function(a.as_double()));
    } else if (auto tensor = a.as_tensor()) {
        assert(&tensor->engine() == this);
        const tensor::Tensor &my_a = static_cast<const tensor::Tensor &>(*tensor);
        if (!tensor::Tensor::supported({my_a.getType()})) {
            return to_default(simple_engine().map(to_simple(a, stash), function, stash), stash);
        }
        CellFunctionFunAdapter cell_function(function);
        return to_value(my_a.apply(cell_function), stash);
    } else {
        return ErrorValue::instance;
    }
}

const Value &
DefaultTensorEngine::join(const Value &a, const Value &b, join_fun_t function, Stash &stash) const
{
    if (a.is_double()) {
        if (b.is_double()) {
            return stash.create<DoubleValue>(function(a.as_double(), b.as_double()));
        } else if (auto tensor_b = b.as_tensor()) {
            assert(&tensor_b->engine() == this);
            const tensor::Tensor &my_b = static_cast<const tensor::Tensor &>(*tensor_b);
            if (!tensor::Tensor::supported({my_b.getType()})) {
                return fallback_join(a, b, function, stash);
            }
            CellFunctionBindLeftAdapter cell_function(function, a.as_double());
            return to_value(my_b.apply(cell_function), stash);
        } else {
            return ErrorValue::instance;
        }
    } else if (auto tensor_a = a.as_tensor()) {
        assert(&tensor_a->engine() == this);
        const tensor::Tensor &my_a = static_cast<const tensor::Tensor &>(*tensor_a);
        if (b.is_double()) {
            if (!tensor::Tensor::supported({my_a.getType()})) {
                return fallback_join(a, b, function, stash);
            }
            CellFunctionBindRightAdapter cell_function(function, b.as_double());
            return to_value(my_a.apply(cell_function), stash);
        } else if (auto tensor_b = b.as_tensor()) {
            assert(&tensor_b->engine() == this);
            const tensor::Tensor &my_b = static_cast<const tensor::Tensor &>(*tensor_b);
            if (!tensor::Tensor::supported({my_a.getType(), my_b.getType()})) {
                return fallback_join(a, b, function, stash);
            }
            if (function == eval::operation::Mul::f) {
                if (my_a.getType() == my_b.getType()) {
                    return to_value(my_a.match(my_b), stash);
                } else {
                    return to_value(my_a.multiply(my_b), stash);
                }
            } else {
                return to_value(my_a.join(function, my_b), stash);
            }
        } else {
            return ErrorValue::instance;
        }
    } else {
        return ErrorValue::instance;
    }
}

const Value &
DefaultTensorEngine::reduce(const Value &a, Aggr aggr, const std::vector<vespalib::string> &dimensions, Stash &stash) const
{
    if (a.is_double()) {
        if (dimensions.empty()) {
            Aggregator &aggregator = Aggregator::create(aggr, stash);
            aggregator.first(a.as_double());
            return stash.create<DoubleValue>(aggregator.result());
        } else {
            return ErrorValue::instance;
        }
    } else if (auto tensor = a.as_tensor()) {
        assert(&tensor->engine() == this);
        const tensor::Tensor &my_a = static_cast<const tensor::Tensor &>(*tensor);
        if (!tensor::Tensor::supported({my_a.getType()})) {
            return fallback_reduce(a, aggr, dimensions, stash);
        }
        switch (aggr) {
        case Aggr::PROD: return to_value(my_a.reduce(eval::operation::Mul::f, dimensions), stash);
        case Aggr::SUM:
            if (dimensions.empty()) {
                return stash.create<eval::DoubleValue>(my_a.sum());
            } else if (dimensions.size() == 1) {
                return to_value(my_a.sum(dimensions[0]), stash);
            } else {
                return to_value(my_a.reduce(eval::operation::Add::f, dimensions), stash);
            }
        case Aggr::MAX: return to_value(my_a.reduce(eval::operation::Max::f, dimensions), stash);
        case Aggr::MIN: return to_value(my_a.reduce(eval::operation::Min::f, dimensions), stash);
        default:
            return fallback_reduce(a, aggr, dimensions, stash);
        }
    } else {
        return ErrorValue::instance;
    }
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
