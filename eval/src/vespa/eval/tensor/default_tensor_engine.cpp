// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "default_tensor_engine.h"
#include "tensor.h"
#include "wrapped_simple_tensor.h"
#include "serialization/typed_binary_format.h"
#include "sparse/sparse_tensor_address_builder.h"
#include "sparse/direct_sparse_tensor_builder.h"
#include "dense/dense_tensor.h"
#include "dense/typed_dense_tensor_builder.h"
#include "dense/dense_dot_product_function.h"
#include "dense/dense_xw_product_function.h"
#include "dense/dense_fast_rename_optimizer.h"
#include "dense/dense_add_dimension_optimizer.h"
#include "dense/dense_remove_dimension_optimizer.h"
#include "dense/dense_inplace_join_function.h"
#include "dense/dense_inplace_map_function.h"
#include "dense/vector_from_doubles_function.h"
#include "dense/dense_tensor_create_function.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".eval.tensor.default_tensor_engine");

namespace vespalib::tensor {

using eval::Aggr;
using eval::Aggregator;
using eval::DoubleValue;
using eval::TensorFunction;
using eval::TensorSpec;
using eval::Value;
using eval::ValueType;
using CellType = eval::ValueType::CellType;
using vespalib::IllegalArgumentException;
using vespalib::make_string;

using map_fun_t = eval::TensorEngine::map_fun_t;
using join_fun_t = eval::TensorEngine::join_fun_t;

namespace {

constexpr size_t UNDEFINED_IDX = std::numeric_limits<size_t>::max();

const eval::TensorEngine &simple_engine() { return eval::SimpleTensorEngine::ref(); }
const eval::TensorEngine &default_engine() { return DefaultTensorEngine::ref(); }

// map tensors to simple tensors before fall-back evaluation

const Value &to_simple(const Value &value, Stash &stash) {
    if (auto tensor = value.as_tensor()) {
        if (auto wrapped = dynamic_cast<const WrappedSimpleTensor *>(tensor)) {
            return wrapped->get();
        }
        nbostream data;
        tensor->engine().encode(*tensor, data);
        return *stash.create<Value::UP>(eval::SimpleTensor::decode(data));
    }
    return value;
}

// map tensors to default tensors after fall-back evaluation

const Value &to_default(const Value &value, Stash &stash) {
    if (auto tensor = value.as_tensor()) {
        if (auto simple = dynamic_cast<const eval::SimpleTensor *>(tensor)) {
            if (!Tensor::supported({simple->type()})) {
                return stash.create<WrappedSimpleTensor>(*simple);
            }
        }
        nbostream data;
        tensor->engine().encode(*tensor, data);
        return *stash.create<Value::UP>(default_engine().decode(data));
    }
    return value;
}

const Value &to_value(std::unique_ptr<Tensor> tensor, Stash &stash) {
    if (tensor->type().is_tensor()) {
        return *stash.create<Value::UP>(std::move(tensor));
    }
    return stash.create<DoubleValue>(tensor->as_double());
}

Value::UP to_value(std::unique_ptr<Tensor> tensor) {
    if (tensor->type().is_tensor()) {
        return tensor;
    }
    return std::make_unique<DoubleValue>(tensor->as_double());
}

const Value &fallback_join(const Value &a, const Value &b, join_fun_t function, Stash &stash) {
    return to_default(simple_engine().join(to_simple(a, stash), to_simple(b, stash), function, stash), stash);
}

const Value &fallback_reduce(const Value &a, eval::Aggr aggr, const std::vector<vespalib::string> &dimensions, Stash &stash) {
    return to_default(simple_engine().reduce(to_simple(a, stash), aggr, dimensions, stash), stash);
}

size_t calculate_cell_index(const ValueType &type, const TensorSpec::Address &address) {
    if (type.dimensions().size() != address.size()) {
        return UNDEFINED_IDX;
    }
    size_t d = 0;
    size_t idx = 0;
    for (const auto &binding: address) {
        const auto &dim = type.dimensions()[d++];
        if ((dim.name != binding.first) || (binding.second.index >= dim.size)) {
            return UNDEFINED_IDX;
        }
        idx *= dim.size;
        idx += binding.second.index;
    }
    return idx;
}

bool build_cell_address(const ValueType &type, const TensorSpec::Address &address,
                        SparseTensorAddressBuilder &builder)
{
    if (type.dimensions().size() != address.size()) {
        return false;
    }
    size_t d = 0;
    builder.clear();
    for (const auto &binding: address) {
        const auto &dim = type.dimensions()[d++];
        if (dim.name != binding.first) {
            return false;
        }
        builder.add(binding.second.name);
    }
    return true;
}

void bad_spec(const TensorSpec &spec) {
    throw IllegalArgumentException(make_string("malformed tensor spec: %s", spec.to_string().c_str()));
}

} // namespace vespalib::tensor::<unnamed>

const DefaultTensorEngine DefaultTensorEngine::_engine;

TensorSpec
DefaultTensorEngine::to_spec(const Value &value) const
{
    if (value.is_double()) {
        return TensorSpec("double").add({}, value.as_double());
    } else if (auto tensor = value.as_tensor()) {
        assert(&tensor->engine() == this);
        const tensor::Tensor &my_tensor = static_cast<const tensor::Tensor &>(*tensor);
        return my_tensor.toSpec();
    } else {
        return TensorSpec("error");
    }
}

struct CallDenseTensorBuilder {
    template <typename CT>
    static Value::UP
    call(const ValueType &type, const TensorSpec &spec)
    {
        TypedDenseTensorBuilder<CT> builder(type);
        for (const auto &cell: spec.cells()) {
            const auto &address = cell.first;
            size_t cell_idx = calculate_cell_index(type, address);
            if (cell_idx == UNDEFINED_IDX) {
                bad_spec(spec);
            }
            builder.insertCell(cell_idx, cell.second);
        }
        return builder.build();
    }
};

Value::UP
DefaultTensorEngine::from_spec(const TensorSpec &spec) const
{
    ValueType type = ValueType::from_spec(spec.type());
    if (type.is_error()) {
        bad_spec(spec);
    } else if (type.is_double()) {
        double value = spec.cells().empty() ? 0.0 : spec.cells().begin()->second.value;
        return std::make_unique<DoubleValue>(value);
    } else if (type.is_dense()) {
        return dispatch_0<CallDenseTensorBuilder>(type.cell_type(), type, spec);
    } else if (type.is_sparse()) {
        DirectSparseTensorBuilder builder(type);
        SparseTensorAddressBuilder address_builder;
        for (const auto &cell: spec.cells()) {
            const auto &address = cell.first;
            if (build_cell_address(type, address, address_builder)) {
                builder.insertCell(address_builder, cell.second);
            } else {
                bad_spec(spec);
            }
        }
        return builder.build();
    }
    return std::make_unique<WrappedSimpleTensor>(eval::SimpleTensor::create(spec));
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
DefaultTensorEngine::encode(const Value &value, nbostream &output) const
{
    if (auto tensor = value.as_tensor()) {
        TypedBinaryFormat::serialize(output, static_cast<const tensor::Tensor &>(*tensor));
    } else {
        TypedBinaryFormat::serialize(output, DenseTensor<double>(ValueType::double_type(), {value.as_double()}));
    }
}

Value::UP
DefaultTensorEngine::decode(nbostream &input) const
{
    return to_value(TypedBinaryFormat::deserialize(input));
}

//-----------------------------------------------------------------------------

const TensorFunction &
DefaultTensorEngine::optimize(const TensorFunction &expr, Stash &stash) const
{
    using Child = TensorFunction::Child;
    Child root(expr);
    std::vector<Child::CREF> nodes({root});
    for (size_t i = 0; i < nodes.size(); ++i) {
        nodes[i].get().get().push_children(nodes);
    }
    LOG(debug, "tensor function before optimization:\n%s\n", root.get().as_string().c_str());
    while (!nodes.empty()) {
        const Child &child = nodes.back();
        child.set(VectorFromDoublesFunction::optimize(child.get(), stash));
        child.set(DenseTensorCreateFunction::optimize(child.get(), stash));
        child.set(DenseDotProductFunction::optimize(child.get(), stash));
        child.set(DenseXWProductFunction::optimize(child.get(), stash));
        child.set(DenseFastRenameOptimizer::optimize(child.get(), stash));
        child.set(DenseAddDimensionOptimizer::optimize(child.get(), stash));
        child.set(DenseRemoveDimensionOptimizer::optimize(child.get(), stash));
        child.set(DenseInplaceMapFunction::optimize(child.get(), stash));
        child.set(DenseInplaceJoinFunction::optimize(child.get(), stash));
        nodes.pop_back();
    }
    LOG(debug, "tensor function after optimization:\n%s\n", root.get().as_string().c_str());
    return root.get();
}

//-----------------------------------------------------------------------------

const Value &
DefaultTensorEngine::map(const Value &a, map_fun_t function, Stash &stash) const
{
    if (auto tensor = a.as_tensor()) {
        assert(&tensor->engine() == this);
        const tensor::Tensor &my_a = static_cast<const tensor::Tensor &>(*tensor);
        if (!tensor::Tensor::supported({my_a.type()})) {
            return to_default(simple_engine().map(to_simple(a, stash), function, stash), stash);
        }
        CellFunctionFunAdapter cell_function(function);
        return to_value(my_a.apply(cell_function), stash);
    } else {
        return stash.create<DoubleValue>(function(a.as_double()));
    }
}

const Value &
DefaultTensorEngine::join(const Value &a, const Value &b, join_fun_t function, Stash &stash) const
{
    if (auto tensor_a = a.as_tensor()) {
        assert(&tensor_a->engine() == this);
        const tensor::Tensor &my_a = static_cast<const tensor::Tensor &>(*tensor_a);
        if (auto tensor_b = b.as_tensor()) {
            assert(&tensor_b->engine() == this);
            const tensor::Tensor &my_b = static_cast<const tensor::Tensor &>(*tensor_b);
            if (!tensor::Tensor::supported({my_a.type(), my_b.type()})) {
                return fallback_join(a, b, function, stash);
            }
            return to_value(my_a.join(function, my_b), stash);
        } else {
            if (!tensor::Tensor::supported({my_a.type()})) {
                return fallback_join(a, b, function, stash);
            }
            CellFunctionBindRightAdapter cell_function(function, b.as_double());
            return to_value(my_a.apply(cell_function), stash);
        }
    } else {
        if (auto tensor_b = b.as_tensor()) {
            assert(&tensor_b->engine() == this);
            const tensor::Tensor &my_b = static_cast<const tensor::Tensor &>(*tensor_b);
            if (!tensor::Tensor::supported({my_b.type()})) {
                return fallback_join(a, b, function, stash);
            }
            CellFunctionBindLeftAdapter cell_function(function, a.as_double());
            return to_value(my_b.apply(cell_function), stash);
        } else {
            return stash.create<DoubleValue>(function(a.as_double(), b.as_double()));
        }
    }
}

const Value &
DefaultTensorEngine::reduce(const Value &a, Aggr aggr, const std::vector<vespalib::string> &dimensions, Stash &stash) const
{
    if (auto tensor = a.as_tensor()) {
        assert(&tensor->engine() == this);
        const tensor::Tensor &my_a = static_cast<const tensor::Tensor &>(*tensor);
        if (!tensor::Tensor::supported({my_a.type()})) {
            return fallback_reduce(a, aggr, dimensions, stash);
        }
        switch (aggr) {
        case Aggr::PROD: return to_value(my_a.reduce(eval::operation::Mul::f, dimensions), stash);
        case Aggr::SUM:
            if (dimensions.empty()) {
                return stash.create<eval::DoubleValue>(my_a.as_double());
            } else {
                return to_value(my_a.reduce(eval::operation::Add::f, dimensions), stash);
            }
        case Aggr::MAX: return to_value(my_a.reduce(eval::operation::Max::f, dimensions), stash);
        case Aggr::MIN: return to_value(my_a.reduce(eval::operation::Min::f, dimensions), stash);
        default:
            return fallback_reduce(a, aggr, dimensions, stash);
        }
    } else {
        Aggregator &aggregator = Aggregator::create(aggr, stash);
        aggregator.first(a.as_double());
        return stash.create<DoubleValue>(aggregator.result());
    }
}

size_t vector_size(const ValueType &type, const vespalib::string &dimension) {
    if (type.is_double()) {
        return 1;
    } else if ((type.dimensions().size() == 1) &&
               (type.dimensions()[0].is_indexed()) &&
               (type.dimensions()[0].name == dimension))
    {
        return type.dimensions()[0].size;
    } else {
        return 0;
    }
}

template <typename OCT>
struct CallAppendVector {
    template <typename CT>
    static void call(const ConstArrayRef<CT> &arr, OCT *&pos) {
        for (CT cell: arr) { *pos++ = cell; }
    }
};

template <typename OCT>
void append_vector(OCT *&pos, const Value &value) {
    if (auto tensor = value.as_tensor()) {
        const DenseTensorView *view = static_cast<const DenseTensorView *>(tensor);
        dispatch_1<CallAppendVector<OCT> >(view->cellsRef(), pos);
    } else {
        *pos++ = value.as_double();
    }
}

template <typename OCT>
const Value &concat_vectors(const Value &a, const Value &b, const vespalib::string &dimension, size_t vector_size, Stash &stash) {
    ArrayRef<OCT> cells = stash.create_array<OCT>(vector_size);
    OCT *pos = cells.begin();
    append_vector<OCT>(pos, a);
    append_vector<OCT>(pos, b);
    assert(pos == cells.end());
    const ValueType &type = stash.create<ValueType>(ValueType::tensor_type({ValueType::Dimension(dimension, vector_size)}, ValueType::unify_cell_types(a.type(), b.type())));
    return stash.create<DenseTensorView>(type, TypedCells(cells));
}

struct CallConcatVectors {
    template <typename OCT>
    static const Value &call(const Value &a, const Value &b, const vespalib::string &dimension, size_t vector_size, Stash &stash) {
        return concat_vectors<OCT>(a, b, dimension, vector_size, stash);
    }
};

const Value &
DefaultTensorEngine::concat(const Value &a, const Value &b, const vespalib::string &dimension, Stash &stash) const
{
    size_t a_size = vector_size(a.type(), dimension);
    size_t b_size = vector_size(b.type(), dimension);
    if ((a_size > 0) && (b_size > 0)) {
        CellType result_cell_type = ValueType::unify_cell_types(a.type(), b.type());
        return dispatch_0<CallConcatVectors>(result_cell_type, a, b, dimension, (a_size + b_size), stash);
    }
    return to_default(simple_engine().concat(to_simple(a, stash), to_simple(b, stash), dimension, stash), stash);
}

const Value &
DefaultTensorEngine::rename(const Value &a, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to, Stash &stash) const
{
    return to_default(simple_engine().rename(to_simple(a, stash), from, to, stash), stash);
}

//-----------------------------------------------------------------------------

}
