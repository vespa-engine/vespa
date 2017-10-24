// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vector>
#include <vespa/vespalib/stllike/string.h>
#include "value_type.h"
#include "operation.h"
#include "aggr.h"

namespace vespalib {

class Stash;

namespace eval {

class Value;
class Tensor;

//-----------------------------------------------------------------------------

/**
 * A tensor function that can be evaluated. A TensorFunction will
 * typically be produced by an implementation-specific compile step
 * that takes an implementation-independent intermediate
 * representation of the tensor function as input (tree of
 * tensor_function::Node objects).
 **/
struct TensorFunction
{
    typedef std::unique_ptr<TensorFunction> UP;

    /**
     * Interface used to obtain input to a tensor function.
     **/
    struct Input {
        virtual const Value &get_tensor(size_t id) const = 0;
        virtual ~Input() {}
    };

    /**
     * Evaluate this tensor function based on the given input. The
     * given stash can be used to store temporary objects that need to
     * be kept alive for the return value to be valid. The return
     * value must conform to the result type indicated by the
     * intermediate representation describing this tensor function.
     *
     * @return result of evaluating this tensor function
     * @param input external stuff needed to evaluate this function
     **/
    virtual const Value &eval(const Input &input, Stash &stash) const = 0;

    virtual ~TensorFunction() {}
};

//-----------------------------------------------------------------------------

struct TensorFunctionVisitor;

namespace tensor_function {

using map_fun_t = double (*)(double);
using join_fun_t = double (*)(double, double);

/**
 * Interface used to describe a tensor function as a tree of nodes
 * with information about operation sequencing and intermediate result
 * types. Each node in the tree will describe a single tensor
 * operation. This is the intermediate representation of a tensor
 * function.
 *
 * The intermediate representation of a tensor function can also be
 * used to evaluate the tensor function it represents directly. This
 * will invoke the immediate API on the tensor engine associated with
 * the input tensors. In other words, the intermediate representation
 * 'compiles to itself'.
 **/
struct Node : public TensorFunction
{
    ValueType result_type;
    Node(const ValueType &result_type_in) : result_type(result_type_in) {}
    virtual void accept(TensorFunctionVisitor &visitor) const = 0;
    Node(const Node &) = delete;
    Node &operator=(const Node &) = delete;
    Node(Node &&) = delete;
    Node &operator=(Node &&) = delete;
};
using Node_UP = std::unique_ptr<Node>;

/**
 * Simple typecasting utility.
 */
template <typename T>
const T *as(const Node &node) { return dynamic_cast<const T *>(&node); }

struct Inject : Node {
    size_t tensor_id;
    Inject(const ValueType &result_type_in,
           size_t tensor_id_in)
        : Node(result_type_in), tensor_id(tensor_id_in) {}
    void accept(TensorFunctionVisitor &visitor) const override;
    const Value &eval(const Input &input, Stash &) const override;
};

struct Reduce : Node {
    Node_UP tensor;
    Aggr aggr;
    std::vector<vespalib::string> dimensions;
    Reduce(const ValueType &result_type_in,
           Node_UP tensor_in,
           Aggr aggr_in,
           const std::vector<vespalib::string> &dimensions_in)
        : Node(result_type_in), tensor(std::move(tensor_in)), aggr(aggr_in), dimensions(dimensions_in) {}
    void accept(TensorFunctionVisitor &visitor) const override;
    const Value &eval(const Input &input, Stash &stash) const override;
};

struct Map : Node {
    Node_UP tensor;
    map_fun_t function;    
    Map(const ValueType &result_type_in,
        Node_UP tensor_in,
        map_fun_t function_in)
        : Node(result_type_in), tensor(std::move(tensor_in)), function(function_in) {}
    void accept(TensorFunctionVisitor &visitor) const override;
    const Value &eval(const Input &input, Stash &stash) const override;
};

struct Join : Node {
    Node_UP lhs_tensor;
    Node_UP rhs_tensor;
    join_fun_t function;    
    Join(const ValueType &result_type_in,
         Node_UP lhs_tensor_in,
         Node_UP rhs_tensor_in,
         join_fun_t function_in)
        : Node(result_type_in), lhs_tensor(std::move(lhs_tensor_in)),
          rhs_tensor(std::move(rhs_tensor_in)), function(function_in) {}
    void accept(TensorFunctionVisitor &visitor) const override;
    const Value &eval(const Input &input, Stash &stash) const override;
};

Node_UP inject(const ValueType &type, size_t tensor_id);
Node_UP reduce(Node_UP tensor, Aggr aggr, const std::vector<vespalib::string> &dimensions);
Node_UP map(Node_UP tensor, map_fun_t function);
Node_UP join(Node_UP lhs_tensor, Node_UP rhs_tensor, join_fun_t function);

} // namespace vespalib::eval::tensor_function

struct TensorFunctionVisitor {
    virtual void visit(const tensor_function::Inject &) = 0;
    virtual void visit(const tensor_function::Reduce &) = 0;
    virtual void visit(const tensor_function::Map    &) = 0;
    virtual void visit(const tensor_function::Join   &) = 0;
    virtual ~TensorFunctionVisitor() {}
};

//-----------------------------------------------------------------------------

} // namespace vespalib::eval
} // namespace vespalib
