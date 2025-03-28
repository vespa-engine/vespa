// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_spec.h"
#include "operation.h"
#include "lazy_params.h"
#include "value_type.h"
#include "value.h"
#include "aggr.h"
#include "interpreted_function.h"
#include "wrap_param.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/overload.h>
#include <span>
#include <string>
#include <variant>

namespace vespalib {

class Stash;
class ObjectVisitor;

namespace eval {

class Tensor;

//-----------------------------------------------------------------------------

/**
 * Interface used to describe a tensor function as a tree of nodes
 * with information about operation sequencing and intermediate
 * results. Each node in the tree describes a single tensor
 * operation. This is the intermediate representation of a tensor
 * function. Note that some nodes in the tree are already indirectly
 * implementation-specific in that they are bound to a specific tensor
 * engine (typically tensor constants and tensor lambdas).
 *
 * A tensor function will initially be created based on a Function
 * (expression AST) and associated type-resolving. In this tree, most
 * nodes will directly represent a single call to the tensor engine
 * immediate API.
 *
 * The generic tree will then be optimized (in-place, bottom-up) where
 * sub-expressions may be replaced with optimized
 * implementation-specific alternatives. Note that multiple nodes in
 * the original representation can be replaced with a single
 * specialized node in the optimized tree.
 *
 * This leaves us with a mixed-mode tree with some generic and some
 * specialized nodes. This tree will then be compiled into a sequence
 * of instructions (each node will map to a single instruction) and
 * evaluated in the context of an interpreted function.
 **/
struct TensorFunction
{
    using CREF = std::reference_wrapper<const TensorFunction>;
    TensorFunction(const TensorFunction &) = delete;
    TensorFunction &operator=(const TensorFunction &) = delete;
    TensorFunction(TensorFunction &&) = delete;
    TensorFunction &operator=(TensorFunction &&) = delete;
    TensorFunction() {}

    /**
     * Reference to a sub-tree. References are replaceable to enable
     * in-place bottom-up optimization.
     **/
    class Child {
    private:
        mutable const TensorFunction *ptr;
    public:
        using CREF = std::reference_wrapper<const Child>;
        Child(const TensorFunction &child) noexcept : ptr(&child) {}
        const TensorFunction &get() const { return *ptr; }
        void set(const TensorFunction &child) const { ptr = &child; }
    };
    virtual const ValueType &result_type() const = 0;
    virtual bool result_is_mutable() const = 0;

    /**
     * Push references to all children (NB: implementation must use
     * Child class for all sub-expression references) on the given
     * vector. This is needed to enable optimization of trees where
     * the core algorithm does not need to know concrete node types.
     *
     * @params children where to put your children references
     **/
    virtual void push_children(std::vector<Child::CREF> &children) const = 0;

    std::vector<Child> copy_children() const {
        std::vector<Child::CREF> child_refs;
        std::vector<Child> children_copy;
        push_children(child_refs);
        for (const auto &child_ref: child_refs) {
            children_copy.emplace_back(child_ref.get().get());
        }
        return children_copy;
    }

    /**
     * Compile this node into a single instruction that can be run by
     * an interpreted function. Sub-expressions are compiled as
     * separate instructions and their results will be available on
     * the value stack during execution.
     *
     * @return instruction representing the operation of this node
     * @param factory the value builder factory used during evaluation
     * @param stash heterogeneous object store
     **/
    virtual InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const = 0;

    // for debug dumping
    std::string as_string() const;
    virtual void visit_self(vespalib::ObjectVisitor &visitor) const;
    virtual void visit_children(vespalib::ObjectVisitor &visitor) const;

    virtual ~TensorFunction() = default;
};

/**
 * Simple typecasting utility.
 */
template <typename T>
const T *as(const TensorFunction &node) { return dynamic_cast<const T *>(&node); }

//-----------------------------------------------------------------------------

namespace tensor_function {

using map_fun_t = vespalib::eval::operation::op1_t;
using join_fun_t = vespalib::eval::operation::op2_t;

class Node : public TensorFunction
{
private:
    ValueType _result_type;
public:
    Node(const ValueType &result_type_in) : _result_type(result_type_in) {}
    const ValueType &result_type() const final override { return _result_type; }
};

//-----------------------------------------------------------------------------

class Leaf : public Node
{
public:
    Leaf(const ValueType &result_type_in) : Node(result_type_in) {}
    void push_children(std::vector<Child::CREF> &children) const final override;
};

class Op1 : public Node
{
private:
    Child _child;
public:
    Op1(const ValueType &result_type_in,
        const TensorFunction &child_in)
        : Node(result_type_in), _child(child_in) {}
    const TensorFunction &child() const { return _child.get(); }
    void push_children(std::vector<Child::CREF> &children) const final override;
    void visit_children(vespalib::ObjectVisitor &visitor) const final override;
};

class Op2 : public Node
{
private:
    Child _lhs;
    Child _rhs;
public:
    Op2(const ValueType &result_type_in,
        const TensorFunction &lhs_in,
        const TensorFunction &rhs_in)
        : Node(result_type_in), _lhs(lhs_in), _rhs(rhs_in) {}
    const TensorFunction &lhs() const { return _lhs.get(); }
    const TensorFunction &rhs() const { return _rhs.get(); }
    void push_children(std::vector<Child::CREF> &children) const final override;
    void visit_children(vespalib::ObjectVisitor &visitor) const final override;
};

//-----------------------------------------------------------------------------

class ConstValue : public Leaf
{
    using Super = Leaf;
private:
    const Value &_value;
public:
    ConstValue(const Value &value_in) : Super(value_in.type()), _value(value_in) {}
    const Value &value() const { return _value; }
    bool result_is_mutable() const override { return false; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const final override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
};

//-----------------------------------------------------------------------------

class Inject : public Leaf
{
    using Super = Leaf;
private:
    size_t _param_idx;
public:
    Inject(const ValueType &result_type_in, size_t param_idx_in)
        : Super(result_type_in), _param_idx(param_idx_in) {}
    size_t param_idx() const { return _param_idx; }
    bool result_is_mutable() const override { return false; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const final override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
};

//-----------------------------------------------------------------------------

class Reduce : public Op1
{
    using Super = Op1;
private:
    Aggr _aggr;
    std::vector<std::string> _dimensions;
public:
    Reduce(const ValueType &result_type_in,
           const TensorFunction &child_in,
           Aggr aggr_in,
           const std::vector<std::string> &dimensions_in)
        : Super(result_type_in, child_in), _aggr(aggr_in), _dimensions(dimensions_in) {}
    Aggr aggr() const { return _aggr; }
    const std::vector<std::string> &dimensions() const { return _dimensions; }
    bool result_is_mutable() const override { return true; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const final override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
};

//-----------------------------------------------------------------------------

class Map : public Op1
{
    using Super = Op1;
private:
    map_fun_t _function;
public:
    Map(const ValueType &result_type_in,
        const TensorFunction &child_in,
        map_fun_t function_in)
        : Super(result_type_in, child_in), _function(function_in) {}
    map_fun_t function() const { return _function; }
    bool result_is_mutable() const override { return true; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
};

//-----------------------------------------------------------------------------

class MapSubspaces : public Op1
{
    using Super = Op1;
private:
    ValueType _inner_type;
    std::shared_ptr<Function const> _lambda;
    NodeTypes _lambda_types;
public:
    MapSubspaces(const ValueType &result_type_in, const TensorFunction &child_in, const Function &lambda_in, NodeTypes lambda_types_in)
      : Super(result_type_in, child_in),
        _inner_type(child_in.result_type().strip_mapped_dimensions()),
        _lambda(lambda_in.shared_from_this()),
        _lambda_types(std::move(lambda_types_in)) {}
    const ValueType &inner_type() const { return _inner_type; }
    const Function &lambda() const { return *_lambda; }
    const NodeTypes &types() const { return _lambda_types; }
    bool result_is_mutable() const override { return true; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const final override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
};

//-----------------------------------------------------------------------------

class Join : public Op2
{
    using Super = Op2;
private:
    join_fun_t _function;
public:
    Join(const ValueType &result_type_in,
         const TensorFunction &lhs_in,
         const TensorFunction &rhs_in,
         join_fun_t function_in)
        : Super(result_type_in, lhs_in, rhs_in), _function(function_in) {}
    join_fun_t function() const { return _function; }
    bool result_is_mutable() const override { return true; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
};

//-----------------------------------------------------------------------------

class Merge : public Op2
{
    using Super = Op2;
private:
    join_fun_t _function;
public:
    Merge(const ValueType &result_type_in,
          const TensorFunction &lhs_in,
          const TensorFunction &rhs_in,
          join_fun_t function_in)
        : Super(result_type_in, lhs_in, rhs_in), _function(function_in) {}
    join_fun_t function() const { return _function; }
    bool result_is_mutable() const override { return true; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
};

//-----------------------------------------------------------------------------

class Concat : public Op2
{
    using Super = Op2;
private:
    std::string _dimension;
public:
    Concat(const ValueType &result_type_in,
           const TensorFunction &lhs_in,
           const TensorFunction &rhs_in,
           const std::string &dimension_in)
        : Super(result_type_in, lhs_in, rhs_in), _dimension(dimension_in) {}
    const std::string &dimension() const { return _dimension; }
    bool result_is_mutable() const override { return true; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const final override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
};

//-----------------------------------------------------------------------------

class CellCast : public Op1
{
    using Super = Op1;
private:
    CellType _cell_type;
public:
    CellCast(const ValueType &result_type_in, const TensorFunction &child_in, CellType cell_type)
        : Super(result_type_in, child_in), _cell_type(cell_type) {}
    CellType cell_type() const { return _cell_type; }
    bool result_is_mutable() const override { return true; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
};

//-----------------------------------------------------------------------------

class Create : public Node
{
    using Super = Node;
private:
    std::map<TensorSpec::Address, Child> _map;
public:
    Create(const ValueType &result_type_in, const std::map<TensorSpec::Address, TensorFunction::CREF> &spec_in)
        : Super(result_type_in), _map()
    {
        for (const auto &cell: spec_in) {
            _map.emplace(cell.first, Child(cell.second));
        }
    }
    const std::map<TensorSpec::Address, Child> &map() const { return _map; }
    // mapping from cell address to index of child that computes the cell value
    using Spec = std::map<TensorSpec::Address, size_t>;
    Spec make_spec() const;
    bool result_is_mutable() const override { return true; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const final override;
    void push_children(std::vector<Child::CREF> &children) const final override;
    void visit_children(vespalib::ObjectVisitor &visitor) const final override;
};

//-----------------------------------------------------------------------------

class Lambda : public Leaf
{
    using Super = Leaf;
private:
    std::vector<size_t> _bindings;
    std::shared_ptr<Function const> _lambda;
    NodeTypes _lambda_types;
public:
    Lambda(const ValueType &result_type_in, const std::vector<size_t> &bindings_in, const Function &lambda_in, NodeTypes lambda_types_in)
        : Super(result_type_in), _bindings(bindings_in), _lambda(lambda_in.shared_from_this()), _lambda_types(std::move(lambda_types_in)) {}
    const std::vector<size_t> &bindings() const { return _bindings; }
    const Function &lambda() const { return *_lambda; }
    const NodeTypes &types() const { return _lambda_types; }
    static TensorSpec create_spec_impl(const ValueType &type, const LazyParams &params, const std::vector<size_t> &bind, const InterpretedFunction &fun);
    TensorSpec create_spec(const LazyParams &params, const InterpretedFunction &fun) const {
        return create_spec_impl(result_type(), params, _bindings, fun);
    }
    bool result_is_mutable() const override { return true; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const final override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
};

//-----------------------------------------------------------------------------

class Peek : public Node
{
    using Super = Node;
public:
    using MyLabel = std::variant<TensorSpec::Label, Child>;
private:
    Child _param;
    std::map<std::string, MyLabel> _map;
public:
    Peek(const ValueType &result_type_in, const TensorFunction &param,
         const std::map<std::string, std::variant<TensorSpec::Label, TensorFunction::CREF>> &spec)
        : Super(result_type_in), _param(param), _map()
    {
        for (const auto &dim: spec) {
            std::visit(vespalib::overload
                       {
                           [&](const TensorSpec::Label &label) {
                               _map.emplace(dim.first, label);
                           },
                           [&](const TensorFunction::CREF &ref) {
                               _map.emplace(dim.first, ref.get());
                           }
                       }, dim.second);
        }
    }
    const std::map<std::string, MyLabel> &map() const { return _map; }
    // a verbatim label or the index of a child that computes the label value:
    using LabelOrChildIndex = std::variant<TensorSpec::Label, size_t>;
    // mapping from dimension name to verbatim label or child index:
    using Spec = std::map<std::string, LabelOrChildIndex>;
    Spec make_spec() const;
    const TensorFunction &param() const { return _param.get(); }
    const ValueType &param_type() const { return _param.get().result_type(); }
    bool result_is_mutable() const override { return true; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const final override;
    void push_children(std::vector<Child::CREF> &children) const final override;
    void visit_children(vespalib::ObjectVisitor &visitor) const final override;
};

//-----------------------------------------------------------------------------

class Rename : public Op1
{
    using Super = Op1;
private:
    std::vector<std::string> _from;
    std::vector<std::string> _to;
public:
    Rename(const ValueType &result_type_in,
           const TensorFunction &child_in,
           const std::vector<std::string> &from_in,
           const std::vector<std::string> &to_in)
        : Super(result_type_in, child_in), _from(from_in), _to(to_in) {}
    const std::vector<std::string> &from() const { return _from; }
    const std::vector<std::string> &to() const { return _to; }
    bool result_is_mutable() const override { return true; }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const final override;
    void visit_self(vespalib::ObjectVisitor &visitor) const override;
};

//-----------------------------------------------------------------------------

class If : public Node
{
private:
    Child _cond;
    Child _true_child;
    Child _false_child;
public:
    If(const ValueType &result_type_in,
       const TensorFunction &cond_in,
       const TensorFunction &true_child_in,
       const TensorFunction &false_child_in)
        : Node(result_type_in), _cond(cond_in), _true_child(true_child_in), _false_child(false_child_in) {}
    const TensorFunction &cond() const { return _cond.get(); }
    const TensorFunction &true_child() const { return _true_child.get(); }
    const TensorFunction &false_child() const { return _false_child.get(); }
    void push_children(std::vector<Child::CREF> &children) const final override;
    bool result_is_mutable() const override {
        return (true_child().result_is_mutable() &&
                false_child().result_is_mutable());
    }
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const final override;
    void visit_children(vespalib::ObjectVisitor &visitor) const final override;
};

//-----------------------------------------------------------------------------

const TensorFunction &const_value(const Value &value, Stash &stash);
const TensorFunction &inject(const ValueType &type, size_t param_idx, Stash &stash);
const TensorFunction &reduce(const TensorFunction &child, Aggr aggr, const std::vector<std::string> &dimensions, Stash &stash);
const TensorFunction &map(const TensorFunction &child, map_fun_t function, Stash &stash);
const TensorFunction &map_subspaces(const TensorFunction &child, const Function &function, NodeTypes node_types, Stash &stash);
const TensorFunction &join(const TensorFunction &lhs, const TensorFunction &rhs, join_fun_t function, Stash &stash);
const TensorFunction &merge(const TensorFunction &lhs, const TensorFunction &rhs, join_fun_t function, Stash &stash);
const TensorFunction &concat(const TensorFunction &lhs, const TensorFunction &rhs, const std::string &dimension, Stash &stash);
const TensorFunction &create(const ValueType &type, const std::map<TensorSpec::Address, TensorFunction::CREF> &spec, Stash &stash);
const TensorFunction &lambda(const ValueType &type, const std::vector<size_t> &bindings, const Function &function, NodeTypes node_types, Stash &stash);
const TensorFunction &cell_cast(const TensorFunction &child, CellType cell_type, Stash &stash);
const TensorFunction &peek(const TensorFunction &param, const std::map<std::string, std::variant<TensorSpec::Label, TensorFunction::CREF>> &spec, Stash &stash);
const TensorFunction &rename(const TensorFunction &child, const std::vector<std::string> &from, const std::vector<std::string> &to, Stash &stash);
const TensorFunction &if_node(const TensorFunction &cond, const TensorFunction &true_child, const TensorFunction &false_child, Stash &stash);

} // namespace vespalib::eval::tensor_function
} // namespace vespalib::eval
} // namespace vespalib
