// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
void Join  ::accept(TensorFunctionVisitor &visitor) const { visitor.visit(*this); }

//-----------------------------------------------------------------------------

const Value &
Inject::eval(const Input &input, Stash &) const
{
    return input.get_tensor(tensor_id);
}

const Value &
Reduce::eval(const Input &input, Stash &stash) const 
{
    const Value &a = tensor->eval(input, stash);
    const TensorEngine &engine = a.as_tensor()->engine();
    return engine.reduce(a, aggr, dimensions, stash);
}

const Value &
Map::eval(const Input &input, Stash &stash) const
{
    const Value &a = tensor->eval(input, stash);
    const TensorEngine &engine = a.as_tensor()->engine();
    return engine.map(a, function, stash);
}

const Value &
Join::eval(const Input &input, Stash &stash) const
{
    const Value &a = lhs_tensor->eval(input, stash);
    const Value &b = rhs_tensor->eval(input, stash);
    const TensorEngine &engine = a.as_tensor()->engine();
    return engine.join(a, b, function, stash);
}

//-----------------------------------------------------------------------------

Node_UP inject(const ValueType &type, size_t tensor_id) {
    return std::make_unique<Inject>(type, tensor_id);
}

Node_UP reduce(Node_UP tensor, Aggr aggr, const std::vector<vespalib::string> &dimensions) {
    ValueType result_type = tensor->result_type.reduce(dimensions);
    return std::make_unique<Reduce>(result_type, std::move(tensor), aggr, dimensions);
}

Node_UP map(Node_UP tensor, map_fun_t function) {
    ValueType result_type = tensor->result_type;
    return std::make_unique<Map>(result_type, std::move(tensor), function);
}

Node_UP join(Node_UP lhs_tensor, Node_UP rhs_tensor, join_fun_t function) {
    ValueType result_type = ValueType::join(lhs_tensor->result_type, rhs_tensor->result_type);
    return std::make_unique<Join>(result_type, std::move(lhs_tensor), std::move(rhs_tensor), function);
}

} // namespace vespalib::eval::tensor_function
} // namespace vespalib::eval
} // namespace vespalib
