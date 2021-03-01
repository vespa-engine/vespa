// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "make_tensor_function.h"
#include "value_codec.h"
#include "tensor_function.h"
#include "node_visitor.h"
#include "node_traverser.h"
#include "tensor_spec.h"
#include "operation.h"
#include "node_types.h"
#include <vespa/eval/eval/llvm/compile_cache.h>

namespace vespalib::eval {

namespace {

using namespace nodes;

//-----------------------------------------------------------------------------

struct TensorFunctionBuilder : public NodeVisitor, public NodeTraverser {
    Stash                     &stash;
    const ValueBuilderFactory &factory;
    const NodeTypes           &types;
    std::vector<TensorFunction::CREF> stack;

    TensorFunctionBuilder(Stash &stash_in, const ValueBuilderFactory &factory_in, const NodeTypes &types_in)
        : stash(stash_in), factory(factory_in), types(types_in), stack() {}
    ~TensorFunctionBuilder() override;

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
        const auto &a = stack.back().get();
        stack.back() = tensor_function::reduce(a, aggr, dimensions, stash);
    }

    void make_map(const Node &, operation::op1_t function) {
        assert(stack.size() >= 1);
        const auto &a = stack.back().get();
        stack.back() = tensor_function::map(a, function, stash);
    }

    void make_join(const Node &, operation::op2_t function) {
        assert(stack.size() >= 2);
        const auto &b = stack.back().get();
        stack.pop_back();
        const auto &a = stack.back().get();
        stack.back() = tensor_function::join(a, b, function, stash);
    }

    void make_merge(const Node &, operation::op2_t function) {
        assert(stack.size() >= 2);
        const auto &b = stack.back().get();
        stack.pop_back();
        const auto &a = stack.back().get();
        stack.back() = tensor_function::merge(a, b, function, stash);
    }

    void make_concat(const Node &, const vespalib::string &dimension) {
        assert(stack.size() >= 2);
        const auto &b = stack.back().get();
        stack.pop_back();
        const auto &a = stack.back().get();
        stack.back() = tensor_function::concat(a, b, dimension, stash);
    }

    void make_cell_cast(const Node &, CellType cell_type) {
        assert(stack.size() >= 1);
        const auto &a = stack.back().get();
        stack.back() = tensor_function::cell_cast(a, cell_type, stash);
    }

    bool maybe_make_const(const Node &node) {
        if (auto create = as<TensorCreate>(node)) {
            bool is_const = true;
            for (size_t i = 0; i < create->num_children(); ++i) {
                is_const &= create->get_child(i).is_const();
            }
            if (is_const) {
                TensorSpec spec(create->type().to_spec());
                for (size_t i = 0; i < create->num_children(); ++i) {
                    spec.add(create->get_child_address(i), create->get_child(i).get_const_value());
                }
                make_const(node, *stash.create<Value::UP>(value_from_spec(spec, factory)));
                return true;
            }
        }
        return false;
    }

    void make_create(const TensorCreate &node) {
        assert(stack.size() >= node.num_children());
        std::map<TensorSpec::Address, TensorFunction::CREF> spec;
        for (size_t idx = node.num_children(); idx-- > 0; ) {
            spec.emplace(node.get_child_address(idx), stack.back());
            stack.pop_back();
        }
        stack.push_back(tensor_function::create(node.type(), spec, stash));
    }

    void make_lambda(const TensorLambda &node) {
        if (node.bindings().empty()) {
            NoParams no_bound_params;
            InterpretedFunction my_fun(factory, node.lambda().root(), types);
            TensorSpec spec = tensor_function::Lambda::create_spec_impl(node.type(), no_bound_params, node.bindings(), my_fun);
            make_const(node, *stash.create<Value::UP>(value_from_spec(spec, factory)));
        } else {
            stack.push_back(tensor_function::lambda(node.type(), node.bindings(), node.lambda(), types.export_types(node.lambda().root()), stash));
        }
    }

    void make_peek(const TensorPeek &node) {
        assert(stack.size() >= node.num_children());
        const TensorFunction &param = stack[stack.size()-node.num_children()];
        std::map<vespalib::string, std::variant<TensorSpec::Label, TensorFunction::CREF>> spec;
        for (auto pos = node.dim_list().rbegin(); pos != node.dim_list().rend(); ++pos) {
            if (pos->second.is_expr()) {
                spec.emplace(pos->first, stack.back());
                stack.pop_back();
            } else {
                size_t dim_idx = param.result_type().dimension_index(pos->first);
                assert(dim_idx != ValueType::Dimension::npos);
                const auto &param_dim = param.result_type().dimensions()[dim_idx];
                if (param_dim.is_mapped()) {
                    spec.emplace(pos->first, pos->second.label);
                } else {
                    spec.emplace(pos->first, as_number(pos->second.label));
                }
            }
        }
        stack.back() = tensor_function::peek(param, spec, stash);
    }

    void make_rename(const Node &, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to) {
        assert(stack.size() >= 1);
        const auto &a = stack.back().get();
        stack.back() = tensor_function::rename(a, from, to, stash);
    }

    void make_if(const Node &) {
        assert(stack.size() >= 3);
        const auto &c = stack.back().get();
        stack.pop_back();
        const auto &b = stack.back().get();
        stack.pop_back();
        const auto &a = stack.back().get();
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
        auto my_fun = Function::create(std::move(my_in), {"x"});
        const auto &token = stash.create<CompileCache::Token::UP>(CompileCache::compile(*my_fun, PassParams::SEPARATE));
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
    void visit(const Error &) override {
        abort();
    }
    void visit(const TensorMap &node) override {
        if (auto op1 = operation::lookup_op1(node.lambda())) {
            make_map(node, op1.value());
        } else {
            const auto &token = stash.create<CompileCache::Token::UP>(CompileCache::compile(node.lambda(), PassParams::SEPARATE));
            make_map(node, token.get()->get().get_function<1>());
        }
    }
    void visit(const TensorJoin &node) override {
        if (auto op2 = operation::lookup_op2(node.lambda())) {
            make_join(node, op2.value());
        } else {
            const auto &token = stash.create<CompileCache::Token::UP>(CompileCache::compile(node.lambda(), PassParams::SEPARATE));
            make_join(node, token.get()->get().get_function<2>());
        }
    }
    void visit(const TensorMerge &node) override {
        const auto &token = stash.create<CompileCache::Token::UP>(CompileCache::compile(node.lambda(), PassParams::SEPARATE));
        make_merge(node, token.get()->get().get_function<2>());
    }
    void visit(const TensorReduce &node) override {
        make_reduce(node, node.aggr(), node.dimensions());
    }
    void visit(const TensorRename &node) override {
        make_rename(node, node.from(), node.to());
    }
    void visit(const TensorConcat &node) override {
        make_concat(node, node.dimension());
    }
    void visit(const TensorCellCast &node) override {
        make_cell_cast(node, node.cell_type());
    }
    void visit(const TensorCreate &node) override {
        make_create(node);
    }
    void visit(const TensorLambda &node) override {
        make_lambda(node);
    }
    void visit(const TensorPeek &node) override {
        make_peek(node);
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
    void visit(const Erf &node) override {
        make_map(node, operation::Erf::f);
    }

    //-------------------------------------------------------------------------

    bool open(const Node &node) override { return !maybe_make_const(node); }
    void close(const Node &node) override { node.accept(*this); }
};

TensorFunctionBuilder::~TensorFunctionBuilder() = default;

} // namespace vespalib::eval::<unnamed>

const TensorFunction &make_tensor_function(const ValueBuilderFactory &factory, const nodes::Node &root, const NodeTypes &types, Stash &stash) {
    TensorFunctionBuilder builder(stash, factory, types);
    root.traverse(builder);
    assert(builder.stack.size() == 1);
    return builder.stack[0];
}

} // namespace vespalib::eval
