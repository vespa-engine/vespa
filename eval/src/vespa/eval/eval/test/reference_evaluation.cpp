// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reference_evaluation.h"
#include "reference_operations.h"

#include <vespa/eval/eval/string_stuff.h>
#include <vespa/eval/eval/node_visitor.h>
#include <vespa/eval/eval/node_traverser.h>
#include <vespa/eval/eval/operation.h>

#include <vespa/vespalib/util/exceptions.h>

#include <functional>
#include <variant>

namespace vespalib::eval::test {

namespace {

using namespace nodes;

//-----------------------------------------------------------------------------

TensorSpec eval_node(const Node &node, const std::vector<TensorSpec> &params);

struct EvalNode : public NodeVisitor {
    const std::vector<TensorSpec> &params;
    TensorSpec result;
    EvalNode(const std::vector<TensorSpec> &params_in)
        : params(params_in), result("error") {}

    //-------------------------------------------------------------------------

    using op1_t = std::function<double(double)>;
    using op2_t = std::function<double(double,double)>;

    static TensorSpec num(double value) {
        return TensorSpec("double").add({}, value);
    }

    //-------------------------------------------------------------------------

    void eval_const(TensorSpec spec) {
        result = spec.normalize();
    }

    void eval_param(size_t idx) {
        assert(idx < params.size());
        result = params[idx].normalize();
    }

    void eval_if(const If &node) {
        if (eval_node(node.cond(), params).as_double() != 0.0) {
            result = eval_node(node.true_expr(), params);
        } else {
            result = eval_node(node.false_expr(), params);
        }
    }

    void eval_map(const Node &a, op1_t op1) {
        result = ReferenceOperations::map(eval_node(a, params), op1);
    }

    void eval_join(const Node &a, const Node &b, op2_t op2) {
        result = ReferenceOperations::join(eval_node(a, params), eval_node(b, params), op2);
    }

    void eval_merge(const Node &a, const Node &b, op2_t op2) {
        result = ReferenceOperations::merge(eval_node(a, params), eval_node(b, params), op2);
    }

    void eval_reduce(const Node &a, Aggr aggr, const std::vector<vespalib::string> &dimensions) {
        result = ReferenceOperations::reduce(eval_node(a, params), aggr, dimensions);
    }

    void eval_rename(const Node &a, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to) {
        result = ReferenceOperations::rename(eval_node(a, params), from, to);
    }

    void eval_concat(const Node &a, const Node &b, const vespalib::string &dimension) {
        result = ReferenceOperations::concat(eval_node(a, params), eval_node(b, params), dimension);
    }

    void eval_cell_cast(const Node &a, CellType cell_type) {
        result = ReferenceOperations::cell_cast(eval_node(a, params), cell_type);
    }

    void eval_create(const TensorCreate &node) {
        std::map<TensorSpec::Address, size_t> spec;
        std::vector<TensorSpec> children;
        for (size_t i = 0; i < node.num_children(); ++i) {
            spec.emplace(node.get_child_address(i), i);
            children.push_back(eval_node(node.get_child(i), params));
        }
        result = ReferenceOperations::create(node.type().to_spec(), spec, children);
    }

    void eval_lambda(const TensorLambda &node) {
        auto fun = [&](const std::vector<size_t> &indexes) {
            std::vector<TensorSpec> lambda_params;
            for (size_t idx: indexes) {
                lambda_params.push_back(num(idx));
            }
            for (size_t param: node.bindings()) {
                assert(param < params.size());
                lambda_params.push_back(params[param]);
            }
            return ReferenceEvaluation::eval(node.lambda(), lambda_params).as_double();
        };
        result = ReferenceOperations::lambda(node.type().to_spec(), fun);
    }

    void eval_peek(const TensorPeek &node) {
        TensorSpec param = eval_node(node.param(), params);
        ValueType param_type = ValueType::from_spec(param.type());
        auto is_indexed = [&](const vespalib::string &dim_name) {
            size_t dim_idx = param_type.dimension_index(dim_name);
            return ((dim_idx != ValueType::Dimension::npos) &&
                    (param_type.dimensions()[dim_idx].is_indexed()));
        };
        std::vector<TensorSpec> children;
        children.push_back(param);
        std::map<vespalib::string, std::variant<TensorSpec::Label, size_t>> spec;
        for (const auto &[name, label]: node.dim_list()) {
            if (label.is_expr()) {
                spec.emplace(name, size_t(children.size()));
                children.push_back(eval_node(*label.expr, params));
            } else {
                if (is_indexed(name)) {
                    spec.emplace(name, TensorSpec::Label(as_number(label.label)));
                } else {
                    spec.emplace(name, TensorSpec::Label(label.label));
                }
            }
        }
        result = ReferenceOperations::peek(spec, children);
    }

    //-------------------------------------------------------------------------

    void visit(const Number &node) override {
        eval_const(num(node.value()));
    }
    void visit(const Symbol &node) override {
        eval_param(node.id());
    }
    void visit(const String &node) override {
        eval_const(num(node.hash()));
    }
    void visit(const In &node) override {
        auto my_op1 = [&](double a) {
            for (size_t i = 0; i < node.num_entries(); ++i) {
                if (a == eval_node(node.get_entry(i), params).as_double()) {
                    return 1.0;
                }
            }
            return 0.0;
        };
        eval_map(node.child(), my_op1);
    }
    void visit(const Neg &node) override {
        eval_map(node.child(), operation::Neg::f);
    }
    void visit(const Not &node) override {
        eval_map(node.child(), operation::Not::f);
    }
    void visit(const If &node) override {
        eval_if(node);
    }
    void visit(const Error &) override {
        abort();
    }
    void visit(const TensorMap &node) override {
        auto my_op1 = [&](double a) {
            return ReferenceEvaluation::eval(node.lambda(), {num(a)}).as_double();
        };
        eval_map(node.child(), my_op1);
    }
    void visit(const TensorJoin &node) override {
        auto my_op2 = [&](double a, double b) {
            return ReferenceEvaluation::eval(node.lambda(), {num(a), num(b)}).as_double();
        };
        eval_join(node.lhs(), node.rhs(), my_op2);
    }
    void visit(const TensorMerge &node) override {
        auto my_op2 = [&](double a, double b) {
            return ReferenceEvaluation::eval(node.lambda(), {num(a), num(b)}).as_double();
        };
        eval_merge(node.lhs(), node.rhs(), my_op2);
    }
    void visit(const TensorReduce &node) override {
        eval_reduce(node.child(), node.aggr(), node.dimensions());
    }
    void visit(const TensorRename &node) override {
        eval_rename(node.child(), node.from(), node.to());
    }
    void visit(const TensorConcat &node) override {
        eval_concat(node.lhs(), node.rhs(), node.dimension());
    }
    void visit(const TensorCellCast &node) override {
        eval_cell_cast(node.child(), node.cell_type());
    }
    void visit(const TensorCreate &node) override {
        eval_create(node);
    }
    void visit(const TensorLambda &node) override {
        eval_lambda(node);
    }
    void visit(const TensorPeek &node) override {
        eval_peek(node);
    }
    void visit(const Add &node) override {
        eval_join(node.lhs(), node.rhs(), operation::Add::f);
    }
    void visit(const Sub &node) override {
        eval_join(node.lhs(), node.rhs(), operation::Sub::f);
    }
    void visit(const Mul &node) override {
        eval_join(node.lhs(), node.rhs(), operation::Mul::f);
    }
    void visit(const Div &node) override {
        eval_join(node.lhs(), node.rhs(), operation::Div::f);
    }
    void visit(const Mod &node) override {
        eval_join(node.lhs(), node.rhs(), operation::Mod::f);
    }
    void visit(const Pow &node) override {
        eval_join(node.lhs(), node.rhs(), operation::Pow::f);
    }
    void visit(const Equal &node) override {
        eval_join(node.lhs(), node.rhs(), operation::Equal::f);
    }
    void visit(const NotEqual &node) override {
        eval_join(node.lhs(), node.rhs(), operation::NotEqual::f);
    }
    void visit(const Approx &node) override {
        eval_join(node.lhs(), node.rhs(), operation::Approx::f);
    }
    void visit(const Less &node) override {
        eval_join(node.lhs(), node.rhs(), operation::Less::f);
    }
    void visit(const LessEqual &node) override {
        eval_join(node.lhs(), node.rhs(), operation::LessEqual::f);
    }
    void visit(const Greater &node) override {
        eval_join(node.lhs(), node.rhs(), operation::Greater::f);
    }
    void visit(const GreaterEqual &node) override {
        eval_join(node.lhs(), node.rhs(), operation::GreaterEqual::f);
    }
    void visit(const And &node) override {
        eval_join(node.lhs(), node.rhs(), operation::And::f);
    }
    void visit(const Or &node) override {
        eval_join(node.lhs(), node.rhs(), operation::Or::f);
    }
    void visit(const Cos &node) override {
        eval_map(node.get_child(0), operation::Cos::f);
    }
    void visit(const Sin &node) override {
        eval_map(node.get_child(0), operation::Sin::f);
    }
    void visit(const Tan &node) override {
        eval_map(node.get_child(0), operation::Tan::f);
    }
    void visit(const Cosh &node) override {
        eval_map(node.get_child(0), operation::Cosh::f);
    }
    void visit(const Sinh &node) override {
        eval_map(node.get_child(0), operation::Sinh::f);
    }
    void visit(const Tanh &node) override {
        eval_map(node.get_child(0), operation::Tanh::f);
    }
    void visit(const Acos &node) override {
        eval_map(node.get_child(0), operation::Acos::f);
    }
    void visit(const Asin &node) override {
        eval_map(node.get_child(0), operation::Asin::f);
    }
    void visit(const Atan &node) override {
        eval_map(node.get_child(0), operation::Atan::f);
    }
    void visit(const Exp &node) override {
        eval_map(node.get_child(0), operation::Exp::f);
    }
    void visit(const Log10 &node) override {
        eval_map(node.get_child(0), operation::Log10::f);
    }
    void visit(const Log &node) override {
        eval_map(node.get_child(0), operation::Log::f);
    }
    void visit(const Sqrt &node) override {
        eval_map(node.get_child(0), operation::Sqrt::f);
    }
    void visit(const Ceil &node) override {
        eval_map(node.get_child(0), operation::Ceil::f);
    }
    void visit(const Fabs &node) override {
        eval_map(node.get_child(0), operation::Fabs::f);
    }
    void visit(const Floor &node) override {
        eval_map(node.get_child(0), operation::Floor::f);
    }
    void visit(const Atan2 &node) override {
        eval_join(node.get_child(0), node.get_child(1), operation::Atan2::f);
    }
    void visit(const Ldexp &node) override {
        eval_join(node.get_child(0), node.get_child(1), operation::Ldexp::f);
    }
    void visit(const Pow2 &node) override {
        eval_join(node.get_child(0), node.get_child(1), operation::Pow::f);
    }
    void visit(const Fmod &node) override {
        eval_join(node.get_child(0), node.get_child(1), operation::Mod::f);
    }
    void visit(const Min &node) override {
        eval_join(node.get_child(0), node.get_child(1), operation::Min::f);
    }
    void visit(const Max &node) override {
        eval_join(node.get_child(0), node.get_child(1), operation::Max::f);
    }
    void visit(const IsNan &node) override {
        eval_map(node.get_child(0), operation::IsNan::f);
    }
    void visit(const Relu &node) override {
        eval_map(node.get_child(0), operation::Relu::f);
    }
    void visit(const Sigmoid &node) override {
        eval_map(node.get_child(0), operation::Sigmoid::f);
    }
    void visit(const Elu &node) override {
        eval_map(node.get_child(0), operation::Elu::f);
    }
    void visit(const Erf &node) override {
        eval_map(node.get_child(0), operation::Erf::f);
    }
};

TensorSpec eval_node(const Node &node, const std::vector<TensorSpec> &params) {
    EvalNode my_eval(params);
    node.accept(my_eval);
    return my_eval.result;
}

} // <unnamed>

TensorSpec
ReferenceEvaluation::eval(const Function &function, const std::vector<TensorSpec> &params) {
    if (function.has_error()) {
        throw IllegalArgumentException("function.has_error()");
    }
    if (function.num_params() != params.size()) {
        throw IllegalArgumentException("function.num_params() != params.size()");
    }
    return eval_node(function.root(), params);
}

} // namespace
