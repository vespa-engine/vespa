// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_function.h"
#include "value.h"
#include "operation.h"
#include "tensor.h"
#include "tensor_engine.h"

namespace vespalib {
namespace eval {
namespace tensor_function {

void Inject::accept(TensorFunctionVisitor &visitor) const { visitor.visit(*this); }
void Reduce::accept(TensorFunctionVisitor &visitor) const { visitor.visit(*this); }
void Map   ::accept(TensorFunctionVisitor &visitor) const { visitor.visit(*this); }
void Apply ::accept(TensorFunctionVisitor &visitor) const { visitor.visit(*this); }

//-----------------------------------------------------------------------------

const Value &
Inject::eval(const Input &input, Stash &) const
{
    return input.get_tensor(tensor_id);
}

const Value &
Reduce::eval(const Input &input, Stash &stash) const 
{
    const Tensor &a = *tensor->eval(input, stash).as_tensor();
    const TensorEngine &engine = a.engine();
    return engine.reduce(a, *op, dimensions, stash);
}

const Value &
Map::eval(const Input &input, Stash &stash) const
{
    const Tensor &a = *tensor->eval(input, stash).as_tensor();
    const TensorEngine &engine = a.engine();
    return engine.map(input.get_map_operation(map_operation_id), a, stash);
}

const Value &
Apply::eval(const Input &input, Stash &stash) const
{
    const Tensor &a = *lhs_tensor->eval(input, stash).as_tensor();
    const Tensor &b = *rhs_tensor->eval(input, stash).as_tensor();
    const TensorEngine &engine = a.engine();
    return engine.apply(*op, a, b, stash);
}

//-----------------------------------------------------------------------------

Node_UP inject(const ValueType &type, size_t tensor_id) {
    return std::make_unique<Inject>(type, tensor_id);
}

Node_UP reduce(Node_UP tensor, const BinaryOperation &op, const std::vector<vespalib::string> &dimensions) {
    ValueType result_type = tensor->result_type.reduce(dimensions);
    return std::make_unique<Reduce>(result_type, std::move(tensor), op.clone(), dimensions);
}

Node_UP map(size_t map_operation_id, Node_UP tensor) {
    ValueType result_type = tensor->result_type;
    return std::make_unique<Map>(result_type, map_operation_id, std::move(tensor));
}

Node_UP apply(const BinaryOperation &op, Node_UP lhs_tensor, Node_UP rhs_tensor) {
    ValueType result_type = ValueType::join(lhs_tensor->result_type, rhs_tensor->result_type);
    return std::make_unique<Apply>(result_type, op.clone(), std::move(lhs_tensor), std::move(rhs_tensor));
}

} // namespace vespalib::eval::tensor_function
} // namespace vespalib::eval
} // namespace vespalib
