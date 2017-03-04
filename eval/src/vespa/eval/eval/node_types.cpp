// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "check_type.h"
#include "function.h"
#include "node_traverser.h"
#include "node_types.h"
#include "node_visitor.h"

namespace vespalib {
namespace eval {
namespace nodes {
namespace {

class State
{
private:
    const std::vector<ValueType>      &_params;
    std::map<const Node *, ValueType> &_type_map;
    std::vector<ValueType>             _let_types;
    std::vector<ValueType>             _types;

public:
    State(const std::vector<ValueType> &params,
          std::map<const Node *, ValueType> &type_map)
        : _params(params), _type_map(type_map), _let_types(), _types() {}

    const ValueType &param_type(size_t idx) {
        assert(idx < _params.size());
        return _params[idx];
    }
    const ValueType &let_type(size_t idx) {
        assert(idx < _let_types.size());
        return _let_types[idx];
    }
    const ValueType &peek(size_t ridx) const {
        assert(_types.size() > ridx);
        return _types[_types.size() - 1 - ridx];
    }
    void bind(size_t prune_cnt, const ValueType &type_ref, const Node &node) {
        ValueType type = type_ref; // need copy since type_ref might be inside _types
        assert(_types.size() >= prune_cnt);
        for (size_t i = 0; i < prune_cnt; ++i) {
            _types.pop_back();
        }
        _types.push_back(type);
        _type_map.emplace(&node, type);
    }
    void push_let(const ValueType &type) {
        _let_types.push_back(type);
    }
    void pop_let() {
        assert(!_let_types.empty());
        _let_types.pop_back();
    }
    void assert_valid_end_state() const {
        assert(_let_types.empty());
        assert(_types.size() == 1);
    }
};

void action_bind_let(State &state) {
    state.push_let(state.peek(0));
}

void action_unbind_let(State &state) {
    state.pop_let();
}

struct TypeResolver : public NodeVisitor, public NodeTraverser {
    State state;
    using action_function = void (*)(State &);
    std::vector<std::pair<const Node *, action_function>> actions;
    TypeResolver(const std::vector<ValueType> &params_in,
                 std::map<const Node *, ValueType> &type_map_out);
    ~TypeResolver();

    //-------------------------------------------------------------------------

    void assert_valid_end_state() const {
        assert(actions.empty());
        state.assert_valid_end_state();
    }

    void add_action(const Node &trigger, action_function action) {
        actions.emplace_back(&trigger, action);
    }

    void check_actions(const Node &node) {
        if (!actions.empty() && (actions.back().first == &node)) {
            actions.back().second(state);
            actions.pop_back();
        }
    }

    //-------------------------------------------------------------------------

    void bind_type(const ValueType &type, const Node &node) {
        state.bind(node.num_children(), type, node);
    }

    bool check_error(const Node &node) {
        for (size_t i = 0; i < node.num_children(); ++i) {
            if (state.peek(i).is_error()) {
                bind_type(ValueType::error_type(), node);
                return true;
            }
        }
        return false;
    }

    void resolve_op1(const Node &node) {
        bind_type(state.peek(0), node);
    }

    void resolve_op2(const Node &node) {
        bind_type(ValueType::join(state.peek(1), state.peek(0)), node);
    }

    //-------------------------------------------------------------------------

    virtual void visit(const Number &node) {
        bind_type(ValueType::double_type(), node);
    }
    virtual void visit(const Symbol &node) {
        if (node.id() >= 0) { // param value
            bind_type(state.param_type(node.id()), node);
        } else { // let binding
            int let_offset = -(node.id() + 1);
            bind_type(state.let_type(let_offset), node);
        }
    }
    virtual void visit(const String &node) {
        bind_type(ValueType::double_type(), node);
    }
    virtual void visit(const Array &node) {
        bind_type(ValueType::double_type(), node);
    }
    virtual void visit(const Neg &node) { resolve_op1(node); }
    virtual void visit(const Not &node) { resolve_op1(node); }
    virtual void visit(const If &node) {
        ValueType true_type = state.peek(1);
        ValueType false_type = state.peek(0);
        if (true_type == false_type) {
            bind_type(true_type, node);
        } else if (true_type.is_tensor() && false_type.is_tensor()) {
            bind_type(ValueType::tensor_type({}), node);
        } else {
            bind_type(ValueType::any_type(), node);
        }
    }
    virtual void visit(const Let &node) {
        bind_type(state.peek(0), node);
    }
    virtual void visit(const Error &node) {
        bind_type(ValueType::error_type(), node);
    }
    virtual void visit(const TensorSum &node) {
        const ValueType &child = state.peek(0);        
        if (node.dimension().empty()) {
            bind_type(child.reduce({}), node);
        } else {
            bind_type(child.reduce({node.dimension()}), node);
        }
    }
    virtual void visit(const TensorMap &node) { resolve_op1(node); }
    virtual void visit(const TensorJoin &node) { resolve_op2(node); }
    virtual void visit(const TensorReduce &node) {
        const ValueType &child = state.peek(0);
        bind_type(child.reduce(node.dimensions()), node);
    }
    virtual void visit(const TensorRename &node) {
        const ValueType &child = state.peek(0);
        bind_type(child.rename(node.from(), node.to()), node);
    }
    virtual void visit(const TensorLambda &node) {
        bind_type(node.type(), node);
    }
    virtual void visit(const TensorConcat &node) {
        bind_type(ValueType::concat(state.peek(1), state.peek(0), node.dimension()), node);
    }

    virtual void visit(const Add &node) { resolve_op2(node); }
    virtual void visit(const Sub &node) { resolve_op2(node); }
    virtual void visit(const Mul &node) { resolve_op2(node); }
    virtual void visit(const Div &node) { resolve_op2(node); }
    virtual void visit(const Pow &node) { resolve_op2(node); }
    virtual void visit(const Equal &node) { resolve_op2(node); }
    virtual void visit(const NotEqual &node) { resolve_op2(node); }
    virtual void visit(const Approx &node) { resolve_op2(node); }
    virtual void visit(const Less &node) { resolve_op2(node); }
    virtual void visit(const LessEqual &node) { resolve_op2(node); }
    virtual void visit(const Greater &node) { resolve_op2(node); }
    virtual void visit(const GreaterEqual &node) { resolve_op2(node); }
    virtual void visit(const In &node) {
        bind_type(ValueType::double_type(), node);
    }
    virtual void visit(const And &node) { resolve_op2(node); }
    virtual void visit(const Or &node) { resolve_op2(node); }
    virtual void visit(const Cos &node) { resolve_op1(node); }
    virtual void visit(const Sin &node) { resolve_op1(node); }
    virtual void visit(const Tan &node) { resolve_op1(node); }
    virtual void visit(const Cosh &node) { resolve_op1(node); }
    virtual void visit(const Sinh &node) { resolve_op1(node); }
    virtual void visit(const Tanh &node) { resolve_op1(node); }
    virtual void visit(const Acos &node) { resolve_op1(node); }
    virtual void visit(const Asin &node) { resolve_op1(node); }
    virtual void visit(const Atan &node) { resolve_op1(node); }
    virtual void visit(const Exp &node) { resolve_op1(node); }
    virtual void visit(const Log10 &node) { resolve_op1(node); }
    virtual void visit(const Log &node) { resolve_op1(node); }
    virtual void visit(const Sqrt &node) { resolve_op1(node); }
    virtual void visit(const Ceil &node) { resolve_op1(node); }
    virtual void visit(const Fabs &node) { resolve_op1(node); }
    virtual void visit(const Floor &node) { resolve_op1(node); }
    virtual void visit(const Atan2 &node) { resolve_op2(node); }
    virtual void visit(const Ldexp &node) { resolve_op2(node); }
    virtual void visit(const Pow2 &node) { resolve_op2(node); }
    virtual void visit(const Fmod &node) { resolve_op2(node); }
    virtual void visit(const Min &node) { resolve_op2(node); }
    virtual void visit(const Max &node) { resolve_op2(node); }
    virtual void visit(const IsNan &node) { resolve_op1(node); }
    virtual void visit(const Relu &node) { resolve_op1(node); }
    virtual void visit(const Sigmoid &node) { resolve_op1(node); }

    //-------------------------------------------------------------------------

    virtual bool open(const Node &node) {
        auto let = as<Let>(node);
        if (let) {
            add_action(let->expr(), action_unbind_let);
            add_action(let->value(), action_bind_let);
        }
        return true;
    }

    virtual void close(const Node &node) {
        if (!check_error(node)) {
            node.accept(*this);
        }
        check_actions(node);
    }
};

TypeResolver::TypeResolver(const std::vector<ValueType> &params_in,
                           std::map<const Node *, ValueType> &type_map_out)
    : state(params_in, type_map_out),
      actions()
{ }

TypeResolver::~TypeResolver() { }

} // namespace vespalib::eval::nodes::<unnamed>
} // namespace vespalib::eval::nodes

NodeTypes::NodeTypes()
    : _not_found(ValueType::any_type()),
      _type_map()
{
}

NodeTypes::NodeTypes(const Function &function, const std::vector<ValueType> &input_types)
    : _not_found(ValueType::error_type()),
      _type_map()
{
    assert(input_types.size() == function.num_params());
    nodes::TypeResolver resolver(input_types, _type_map);
    function.root().traverse(resolver);
    resolver.assert_valid_end_state();
}

const ValueType &
NodeTypes::get_type(const nodes::Node &node) const
{
    auto pos = _type_map.find(&node);
    if (pos == _type_map.end()) {
        return _not_found;
    }
    return pos->second;
}

} // namespace vespalib::eval
} // namespace vespalib
