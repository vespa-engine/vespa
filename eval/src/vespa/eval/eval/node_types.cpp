// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "check_type.h"
#include "node_traverser.h"
#include "node_types.h"

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

    void visit(const Number &node) override {
        bind_type(ValueType::double_type(), node);
    }
    void visit(const Symbol &node) override {
        if (node.id() >= 0) { // param value
            bind_type(state.param_type(node.id()), node);
        } else { // let binding
            int let_offset = -(node.id() + 1);
            bind_type(state.let_type(let_offset), node);
        }
    }
    void visit(const String &node) override {
        bind_type(ValueType::double_type(), node);
    }
    void visit(const Array &node) override {
        bind_type(ValueType::double_type(), node);
    }
    void visit(const Neg &node) override { resolve_op1(node); }
    void visit(const Not &node) override { resolve_op1(node); }
    void visit(const If &node) override {
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
    void visit(const Let &node) override {
        bind_type(state.peek(0), node);
    }
    void visit(const Error &node) override {
        bind_type(ValueType::error_type(), node);
    }
    void visit(const TensorSum &node) override {
        const ValueType &child = state.peek(0);        
        if (node.dimension().empty()) {
            bind_type(child.reduce({}), node);
        } else {
            bind_type(child.reduce({node.dimension()}), node);
        }
    }
    void visit(const TensorMap &node) override { resolve_op1(node); }
    void visit(const TensorJoin &node) override { resolve_op2(node); }
    void visit(const TensorReduce &node) override {
        const ValueType &child = state.peek(0);
        bind_type(child.reduce(node.dimensions()), node);
    }
    void visit(const TensorRename &node) override {
        const ValueType &child = state.peek(0);
        bind_type(child.rename(node.from(), node.to()), node);
    }
    void visit(const TensorLambda &node) override {
        bind_type(node.type(), node);
    }
    void visit(const TensorConcat &node) override {
        bind_type(ValueType::concat(state.peek(1), state.peek(0), node.dimension()), node);
    }

    void visit(const Add &node) override { resolve_op2(node); }
    void visit(const Sub &node) override { resolve_op2(node); }
    void visit(const Mul &node) override { resolve_op2(node); }
    void visit(const Div &node) override { resolve_op2(node); }
    void visit(const Pow &node) override { resolve_op2(node); }
    void visit(const Equal &node) override { resolve_op2(node); }
    void visit(const NotEqual &node) override { resolve_op2(node); }
    void visit(const Approx &node) override { resolve_op2(node); }
    void visit(const Less &node) override { resolve_op2(node); }
    void visit(const LessEqual &node) override { resolve_op2(node); }
    void visit(const Greater &node) override { resolve_op2(node); }
    void visit(const GreaterEqual &node) override { resolve_op2(node); }
    void visit(const In &node) override {
        bind_type(ValueType::double_type(), node);
    }
    void visit(const And &node) override { resolve_op2(node); }
    void visit(const Or &node) override { resolve_op2(node); }
    void visit(const Cos &node) override { resolve_op1(node); }
    void visit(const Sin &node) override { resolve_op1(node); }
    void visit(const Tan &node) override { resolve_op1(node); }
    void visit(const Cosh &node) override { resolve_op1(node); }
    void visit(const Sinh &node) override { resolve_op1(node); }
    void visit(const Tanh &node) override { resolve_op1(node); }
    void visit(const Acos &node) override { resolve_op1(node); }
    void visit(const Asin &node) override { resolve_op1(node); }
    void visit(const Atan &node) override { resolve_op1(node); }
    void visit(const Exp &node) override { resolve_op1(node); }
    void visit(const Log10 &node) override { resolve_op1(node); }
    void visit(const Log &node) override { resolve_op1(node); }
    void visit(const Sqrt &node) override { resolve_op1(node); }
    void visit(const Ceil &node) override { resolve_op1(node); }
    void visit(const Fabs &node) override { resolve_op1(node); }
    void visit(const Floor &node) override { resolve_op1(node); }
    void visit(const Atan2 &node) override { resolve_op2(node); }
    void visit(const Ldexp &node) override { resolve_op2(node); }
    void visit(const Pow2 &node) override { resolve_op2(node); }
    void visit(const Fmod &node) override { resolve_op2(node); }
    void visit(const Min &node) override { resolve_op2(node); }
    void visit(const Max &node) override { resolve_op2(node); }
    void visit(const IsNan &node) override { resolve_op1(node); }
    void visit(const Relu &node) override { resolve_op1(node); }
    void visit(const Sigmoid &node) override { resolve_op1(node); }

    //-------------------------------------------------------------------------

    bool open(const Node &node) override {
        auto let = as<Let>(node);
        if (let) {
            add_action(let->expr(), action_unbind_let);
            add_action(let->value(), action_bind_let);
        }
        return true;
    }

    void close(const Node &node) override {
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
