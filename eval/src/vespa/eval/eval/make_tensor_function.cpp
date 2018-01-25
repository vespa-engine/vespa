// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "make_tensor_function.h"
#include "tensor_function.h"
#include "node_visitor.h"
#include "node_traverser.h"
#include "tensor_spec.h"
#include "operation.h"
#include "node_types.h"
#include "tensor_engine.h"
#include <vespa/eval/eval/llvm/compile_cache.h>

namespace vespalib::eval {

namespace {

using namespace nodes;
using map_fun_t = double (*)(double);
using join_fun_t = double (*)(double, double);

//-----------------------------------------------------------------------------

bool step_labels(std::vector<double> &labels, const ValueType &type) {
    for (size_t idx = labels.size(); idx-- > 0; ) {
        labels[idx] += 1.0;
        if (size_t(labels[idx]) < type.dimensions()[idx].size) {
            return true;
        } else {
            labels[idx] = 0.0;
        }
    }
    return false;
}

// TODO(havardpe): generic function pointer resolving for all single
//                 operation lambdas.

template <typename OP2>
bool is_op2(const Function &lambda) {
    if (lambda.num_params() == 2) {
        if (auto op2 = as<OP2>(lambda.root())) {
            auto sym1 = as<Symbol>(op2->lhs());
            auto sym2 = as<Symbol>(op2->rhs());
            return (sym1 && sym2 && (sym1->id() != sym2->id()));
        }
    }
    return false;
}

//-----------------------------------------------------------------------------

struct TensorFunctionBuilder : public NodeVisitor, public NodeTraverser {
    Stash              &stash;
    const TensorEngine &tensor_engine;
    const NodeTypes    &types;
    std::vector<tensor_function::Node::CREF> stack;

    TensorFunctionBuilder(Stash &stash_in, const TensorEngine &tensor_engine_in, const NodeTypes &types_in)
        : stash(stash_in), tensor_engine(tensor_engine_in), types(types_in), stack() {}

    //-------------------------------------------------------------------------

    void make_const(const Node &, const Value &value) {
        stack.emplace_back(tensor_function::const_value(value, stash));
    }

    void make_inject(const Node &node, size_t param_idx) {
        const ValueType &type = types.get_type(node);
        stack.emplace_back(tensor_function::inject(type, param_idx, stash));
    }

    void make_reduce(const Node &, Aggr aggr, const std::vector<vespalib::string> &dimensions) {
        assert(stack.size() >= 1);
        const auto &a = stack.back();
        stack.back() = tensor_function::reduce(a, aggr, dimensions, stash);
    }

    void make_map(const Node &, map_fun_t function) {
        assert(stack.size() >= 1);
        const auto &a = stack.back();
        stack.back() = tensor_function::map(a, function, stash);
    }

    void make_join(const Node &, join_fun_t function) {
        assert(stack.size() >= 2);
        const auto &b = stack.back();
        stack.pop_back();
        const auto &a = stack.back();
        stack.back() = tensor_function::join(a, b, function, stash);
    }

    void make_concat(const Node &, const vespalib::string &dimension) {
        assert(stack.size() >= 2);
        const auto &b = stack.back();
        stack.pop_back();
        const auto &a = stack.back();
        stack.back() = tensor_function::concat(a, b, dimension, stash);
    }

    void make_rename(const Node &, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to) {
        assert(stack.size() >= 1);
        const auto &a = stack.back();
        stack.back() = tensor_function::rename(a, from, to, stash);
    }

    void make_if(const Node &) {
        assert(stack.size() >= 3);
        const auto &c = stack.back();
        stack.pop_back();
        const auto &b = stack.back();
        stack.pop_back();
        const auto &a = stack.back();
        stack.back() = tensor_function::if_node(a, b, c, stash);
    }

    //-------------------------------------------------------------------------

    void visit(const Number &node) override {
        make_const(node, stash.create<DoubleValue>(node.value()));
    }
    void visit(const Symbol &node) override {
        make_inject(node, node.id());
    }
    void visit(const String &node) override {
        make_const(node, stash.create<DoubleValue>(node.hash()));
    }
    void visit(const In &node) override {
        auto my_in = std::make_unique<In>(std::make_unique<Symbol>(0));
        for (size_t i = 0; i < node.num_entries(); ++i) {
            my_in->add_entry(std::make_unique<Number>(node.get_entry(i).get_const_value()));
        }
        Function my_fun(std::move(my_in), {"x"});
        const auto &token = stash.create<CompileCache::Token::UP>(CompileCache::compile(my_fun, PassParams::SEPARATE));
        make_map(node, token.get()->get().get_function<1>());
    }
    void visit(const Neg &node) override {
        make_map(node, operation::Neg::f);
    }
    void visit(const Not &node) override {
        make_map(node, operation::Not::f);
    }
    void visit(const If &node) override {
        make_if(node);
    }
    void visit(const Error &node) override {
        make_const(node, ErrorValue::instance);
    }
    void visit(const TensorMap &node) override {
        const auto &token = stash.create<CompileCache::Token::UP>(CompileCache::compile(node.lambda(), PassParams::SEPARATE));
        make_map(node, token.get()->get().get_function<1>());
    }
    void visit(const TensorJoin &node) override {
        if (is_op2<Mul>(node.lambda())) {
            make_join(node, operation::Mul::f);
        } else if (is_op2<Add>(node.lambda())) {
            make_join(node, operation::Add::f);
        } else {
            const auto &token = stash.create<CompileCache::Token::UP>(CompileCache::compile(node.lambda(), PassParams::SEPARATE));
            make_join(node, token.get()->get().get_function<2>());
        }
    }
    void visit(const TensorReduce &node) override {
        make_reduce(node, node.aggr(), node.dimensions());
    }
    void visit(const TensorRename &node) override {
        make_rename(node, node.from(), node.to());
    }
    void visit(const TensorLambda &node) override {
        const auto &type = node.type();
        TensorSpec spec(type.to_spec());
        const auto &token = stash.create<CompileCache::Token::UP>(CompileCache::compile(node.lambda(), PassParams::ARRAY));
        auto fun = token.get()->get().get_function();
        std::vector<double> params(type.dimensions().size(), 0.0);
        assert(token.get()->get().num_params() == params.size());
        do {
            TensorSpec::Address addr;
            for (size_t i = 0; i < params.size(); ++i) {
                addr.emplace(type.dimensions()[i].name, size_t(params[i]));
            }
            spec.add(addr, fun(&params[0]));
        } while (step_labels(params, type));
        make_const(node, *stash.create<Value::UP>(tensor_engine.from_spec(spec)));
    }
    void visit(const TensorConcat &node) override {
        make_concat(node, node.dimension());
    }
    void visit(const Add &node) override {
        make_join(node, operation::Add::f);
    }
    void visit(const Sub &node) override {
        make_join(node, operation::Sub::f);
    }
    void visit(const Mul &node) override {
        make_join(node, operation::Mul::f);
    }
    void visit(const Div &node) override {
        make_join(node, operation::Div::f);
    }
    void visit(const Mod &node) override {
        make_join(node, operation::Mod::f);
    }
    void visit(const Pow &node) override {
        make_join(node, operation::Pow::f);
    }
    void visit(const Equal &node) override {
        make_join(node, operation::Equal::f);
    }
    void visit(const NotEqual &node) override {
        make_join(node, operation::NotEqual::f);
    }
    void visit(const Approx &node) override {
        make_join(node, operation::Approx::f);
    }
    void visit(const Less &node) override {
        make_join(node, operation::Less::f);
    }
    void visit(const LessEqual &node) override {
        make_join(node, operation::LessEqual::f);
    }
    void visit(const Greater &node) override {
        make_join(node, operation::Greater::f);
    }
    void visit(const GreaterEqual &node) override {
        make_join(node, operation::GreaterEqual::f);
    }
    void visit(const And &node) override {
        make_join(node, operation::And::f);
    }
    void visit(const Or &node) override {
        make_join(node, operation::Or::f);
    }
    void visit(const Cos &node) override {
        make_map(node, operation::Cos::f);
    }
    void visit(const Sin &node) override {
        make_map(node, operation::Sin::f);
    }
    void visit(const Tan &node) override {
        make_map(node, operation::Tan::f);
    }
    void visit(const Cosh &node) override {
        make_map(node, operation::Cosh::f);
    }
    void visit(const Sinh &node) override {
        make_map(node, operation::Sinh::f);
    }
    void visit(const Tanh &node) override {
        make_map(node, operation::Tanh::f);
    }
    void visit(const Acos &node) override {
        make_map(node, operation::Acos::f);
    }
    void visit(const Asin &node) override {
        make_map(node, operation::Asin::f);
    }
    void visit(const Atan &node) override {
        make_map(node, operation::Atan::f);
    }
    void visit(const Exp &node) override {
        make_map(node, operation::Exp::f);
    }
    void visit(const Log10 &node) override {
        make_map(node, operation::Log10::f);
    }
    void visit(const Log &node) override {
        make_map(node, operation::Log::f);
    }
    void visit(const Sqrt &node) override {
        make_map(node, operation::Sqrt::f);
    }
    void visit(const Ceil &node) override {
        make_map(node, operation::Ceil::f);
    }
    void visit(const Fabs &node) override {
        make_map(node, operation::Fabs::f);
    }
    void visit(const Floor &node) override {
        make_map(node, operation::Floor::f);
    }
    void visit(const Atan2 &node) override {
        make_join(node, operation::Atan2::f);
    }
    void visit(const Ldexp &node) override {
        make_join(node, operation::Ldexp::f);
    }
    void visit(const Pow2 &node) override {
        make_join(node, operation::Pow::f);
    }
    void visit(const Fmod &node) override {
        make_join(node, operation::Mod::f);
    }
    void visit(const Min &node) override {
        make_join(node, operation::Min::f);
    }
    void visit(const Max &node) override {
        make_join(node, operation::Max::f);
    }
    void visit(const IsNan &node) override {
        make_map(node, operation::IsNan::f);
    }
    void visit(const Relu &node) override {
        make_map(node, operation::Relu::f);
    }
    void visit(const Sigmoid &node) override {
        make_map(node, operation::Sigmoid::f);
    }
    void visit(const Elu &node) override {
        make_map(node, operation::Elu::f);
    }

    //-------------------------------------------------------------------------

    bool open(const Node &) override { return true; }
    void close(const Node &node) override { node.accept(*this); }
};

} // namespace vespalib::eval::<unnamed>

const TensorFunction &make_tensor_function(const TensorEngine &engine, const nodes::Node &root, const NodeTypes &types, Stash &stash) {
    TensorFunctionBuilder builder(stash, engine, types);
    root.traverse(builder);
    assert(builder.stack.size() == 1);
    return builder.stack[0];
}

} // namespace vespalib::eval
