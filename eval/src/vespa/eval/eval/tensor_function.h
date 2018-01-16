// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vector>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/arrayref.h>
#include "lazy_params.h"
#include "value_type.h"
#include "value.h"
#include "aggr.h"

namespace vespalib {

class Stash;

namespace eval {

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
    /**
     * Evaluate this tensor function based on the given
     * parameters. The given stash can be used to store temporary
     * objects that need to be kept alive for the return value to be
     * valid. The return value must conform to the result type
     * indicated by the intermediate representation describing this
     * tensor function.
     *
     * @return result of evaluating this tensor function
     * @param params external values needed to evaluate this function
     * @param stash heterogeneous object store
     **/
    virtual const Value &eval(const LazyParams &params, Stash &stash) const = 0;
    virtual ~TensorFunction() {}
};

/**
 * Simple typecasting utility.
 */
template <typename T>
const T *as(const TensorFunction &node) { return dynamic_cast<const T *>(&node); }

//-----------------------------------------------------------------------------

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
 *
 * The reason for using the top-level TensorFunction interface when
 * referencing downwards in the tree is to enable mixed-mode execution
 * resulting from partial optimization where the intermediate
 * representation is partially replaced by implementation-specific
 * tensor functions, which may or may not rely on lower-level tensor
 * functions that may in turn be mixed-mode.
 **/
struct Node : public TensorFunction
{
    /**
     * Reference to a sub-tree. References are replaceable to enable
     * in-place bottom-up optimization during compilation.
     **/
    class Child {
    private:
        mutable const TensorFunction *ptr;
    public:
        using CREF = std::reference_wrapper<const Child>;
        Child(const TensorFunction &child) : ptr(&child) {}
        const TensorFunction &get() const { return *ptr; }
        void set(const TensorFunction &child) const { ptr = &child; }
    };
    const ValueType result_type;
    Node(const ValueType &result_type_in) : result_type(result_type_in) {}
    Node(const Node &) = delete;
    Node &operator=(const Node &) = delete;
    Node(Node &&) = delete;
    Node &operator=(Node &&) = delete;
    virtual void push_children(std::vector<Child::CREF> &children) const = 0;
};

struct Inject : Node {
    const size_t tensor_id;
    Inject(const ValueType &result_type_in,
           size_t tensor_id_in)
        : Node(result_type_in), tensor_id(tensor_id_in) {}
    const Value &eval(const LazyParams &params, Stash &) const override;
    void push_children(std::vector<Child::CREF> &children) const override;
};

struct Reduce : Node {
    Child tensor;
    const Aggr aggr;
    const std::vector<vespalib::string> dimensions;
    Reduce(const ValueType &result_type_in,
           const TensorFunction &tensor_in,
           Aggr aggr_in,
           const std::vector<vespalib::string> &dimensions_in)
        : Node(result_type_in), tensor(tensor_in), aggr(aggr_in), dimensions(dimensions_in) {}
    const Value &eval(const LazyParams &params, Stash &stash) const override;
    void push_children(std::vector<Child::CREF> &children) const override;
};

struct Map : Node {
    Child tensor;
    const map_fun_t function;    
    Map(const ValueType &result_type_in,
        const TensorFunction &tensor_in,
        map_fun_t function_in)
        : Node(result_type_in), tensor(tensor_in), function(function_in) {}
    const Value &eval(const LazyParams &params, Stash &stash) const override;
    void push_children(std::vector<Child::CREF> &children) const override;
};

struct Join : Node {
    Child lhs_tensor;
    Child rhs_tensor;
    const join_fun_t function;    
    Join(const ValueType &result_type_in,
         const TensorFunction &lhs_tensor_in,
         const TensorFunction &rhs_tensor_in,
         join_fun_t function_in)
        : Node(result_type_in), lhs_tensor(lhs_tensor_in),
          rhs_tensor(rhs_tensor_in), function(function_in) {}
    const Value &eval(const LazyParams &params, Stash &stash) const override;
    void push_children(std::vector<Child::CREF> &children) const override;
};

const Node &inject(const ValueType &type, size_t tensor_id, Stash &stash);
const Node &reduce(const Node &tensor, Aggr aggr, const std::vector<vespalib::string> &dimensions, Stash &stash);
const Node &map(const Node &tensor, map_fun_t function, Stash &stash);
const Node &join(const Node &lhs_tensor, const Node &rhs_tensor, join_fun_t function, Stash &stash);

} // namespace vespalib::eval::tensor_function
} // namespace vespalib::eval
} // namespace vespalib
