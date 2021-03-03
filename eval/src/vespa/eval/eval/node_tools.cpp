// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "node_tools.h"
#include <vespa/eval/eval/node_traverser.h>
#include <vespa/eval/eval/node_visitor.h>

using namespace vespalib::eval;
using namespace vespalib::eval::nodes;

namespace vespalib::eval {

namespace {

struct CountParams : NodeTraverser, EmptyNodeVisitor {
    size_t result = 0;
    void visit(const Symbol &symbol) override {
        result = std::max(result, symbol.id() + 1);
    }
    bool open(const Node &) override { return true; }
    void close(const Node &node) override { node.accept(*this); }
};

struct CopyNode : NodeTraverser, NodeVisitor {

    std::unique_ptr<Error> error;
    std::vector<Node_UP> stack;

    CopyNode() : error(), stack() {}
    ~CopyNode() override;

    Node_UP result() {
        if (error) {
            return std::move(error);
        }
        if (stack.size() != 1) {
            return std::make_unique<Error>("invalid result stack");
        }
        return std::move(stack.back());
    }

    //-------------------------------------------------------------------------

    void fail(const vespalib::string &msg) {
        if (!error) {
            error = std::make_unique<Error>(msg);
        }
    }

    void not_implemented(const Node &) {
        fail("not implemented");
    }

    std::vector<Node_UP> get_children(size_t n) {
        std::vector<Node_UP> result;
        if (stack.size() >= n) {
            for (size_t i = 0; i < n; ++i) {
                result.push_back(std::move(stack[stack.size() - (n - i)]));
            }
            stack.resize(stack.size() - n);
        } else {
            fail("stack underflow");
            for (size_t i = 0; i < n; ++i) {
                result.push_back(std::make_unique<Error>("placeholder"));
            }
        }
        return result;
    }

    //-------------------------------------------------------------------------

    void wire_operator(Operator_UP op) {
        auto list = get_children(2);
        op->bind(std::move(list[0]), std::move(list[1]));
        stack.push_back(std::move(op));
    }

    void wire_call(Call_UP call) {
        auto list = get_children(call->num_params());
        for (size_t i = 0; i < list.size(); ++i) {
            call->bind_next(std::move(list[i]));
        }
        stack.push_back(std::move(call));
    }

    template <typename T> void copy_operator(const T &) { wire_operator(T::create()); }
    template <typename T> void copy_call(const T &) { wire_call(T::create()); }

    //-------------------------------------------------------------------------

    // basic nodes
    void visit(const Number &node) override {
        stack.push_back(std::make_unique<Number>(node.value()));
    }
    void visit(const Symbol &node) override {
        stack.push_back(std::make_unique<Symbol>(node.id()));
    }
    void visit(const String &node) override {
        stack.push_back(std::make_unique<String>(node.value()));
    }
    void visit(const In &node) override {
        for (size_t i = 0; i < node.num_entries(); ++i) {
            // only String/Number allowed here; copy to stack
            node.get_entry(i).accept(*this);
        }
        auto list = get_children(node.num_entries() + 1);
        auto my_node = std::make_unique<In>(std::move(list[0]));
        for (size_t i = 1; i < list.size(); ++i) {
            my_node->add_entry(std::move(list[i]));
        }
        stack.push_back(std::move(my_node));
    }
    void visit(const Neg &) override {
        auto list = get_children(1);
        stack.push_back(std::make_unique<Neg>(std::move(list[0])));
    }
    void visit(const Not &) override {
        auto list = get_children(1);
        stack.push_back(std::make_unique<Not>(std::move(list[0])));
    }
    void visit(const If &node) override {
        auto list = get_children(3);
        stack.push_back(std::make_unique<If>(std::move(list[0]), std::move(list[1]), std::move(list[2]), node.p_true()));
    }
    void visit(const Error &node) override {
        stack.push_back(std::make_unique<Error>(node.message()));
    }

    // tensor nodes
    void visit(const TensorMap      &node) override { not_implemented(node); }
    void visit(const TensorJoin     &node) override { not_implemented(node); }
    void visit(const TensorMerge    &node) override { not_implemented(node); }
    void visit(const TensorReduce   &node) override { not_implemented(node); }
    void visit(const TensorRename   &node) override { not_implemented(node); }
    void visit(const TensorConcat   &node) override { not_implemented(node); }
    void visit(const TensorCellCast &node) override { not_implemented(node); }
    void visit(const TensorCreate   &node) override { not_implemented(node); }
    void visit(const TensorLambda   &node) override { not_implemented(node); }
    void visit(const TensorPeek     &node) override { not_implemented(node); }

    // operator nodes
    void visit(const Add            &node) override { copy_operator(node); }
    void visit(const Sub            &node) override { copy_operator(node); }
    void visit(const Mul            &node) override { copy_operator(node); }
    void visit(const Div            &node) override { copy_operator(node); }
    void visit(const Mod            &node) override { copy_operator(node); }
    void visit(const Pow            &node) override { copy_operator(node); }
    void visit(const Equal          &node) override { copy_operator(node); }
    void visit(const NotEqual       &node) override { copy_operator(node); }
    void visit(const Approx         &node) override { copy_operator(node); }
    void visit(const Less           &node) override { copy_operator(node); }
    void visit(const LessEqual      &node) override { copy_operator(node); }
    void visit(const Greater        &node) override { copy_operator(node); }
    void visit(const GreaterEqual   &node) override { copy_operator(node); }
    void visit(const And            &node) override { copy_operator(node); }
    void visit(const Or             &node) override { copy_operator(node); }

    // call nodes
    void visit(const Cos            &node) override { copy_call(node); }
    void visit(const Sin            &node) override { copy_call(node); }
    void visit(const Tan            &node) override { copy_call(node); }
    void visit(const Cosh           &node) override { copy_call(node); }
    void visit(const Sinh           &node) override { copy_call(node); }
    void visit(const Tanh           &node) override { copy_call(node); }
    void visit(const Acos           &node) override { copy_call(node); }
    void visit(const Asin           &node) override { copy_call(node); }
    void visit(const Atan           &node) override { copy_call(node); }
    void visit(const Exp            &node) override { copy_call(node); }
    void visit(const Log10          &node) override { copy_call(node); }
    void visit(const Log            &node) override { copy_call(node); }
    void visit(const Sqrt           &node) override { copy_call(node); }
    void visit(const Ceil           &node) override { copy_call(node); }
    void visit(const Fabs           &node) override { copy_call(node); }
    void visit(const Floor          &node) override { copy_call(node); }
    void visit(const Atan2          &node) override { copy_call(node); }
    void visit(const Ldexp          &node) override { copy_call(node); }
    void visit(const Pow2           &node) override { copy_call(node); }
    void visit(const Fmod           &node) override { copy_call(node); }
    void visit(const Min            &node) override { copy_call(node); }
    void visit(const Max            &node) override { copy_call(node); }
    void visit(const IsNan          &node) override { copy_call(node); }
    void visit(const Relu           &node) override { copy_call(node); }
    void visit(const Sigmoid        &node) override { copy_call(node); }
    void visit(const Elu            &node) override { copy_call(node); }
    void visit(const Erf            &node) override { copy_call(node); }

    // traverse nodes
    bool open(const Node &) override { return !error; }
    void close(const Node &node) override { node.accept(*this); }
};

CopyNode::~CopyNode() = default;

} // namespace vespalib::eval::<unnamed>

size_t
NodeTools::min_num_params(const Node &node)
{
    CountParams count_params;
    node.traverse(count_params);
    return count_params.result;
}

Node_UP
NodeTools::copy(const Node &node)
{
    CopyNode copy_node;
    node.traverse(copy_node);
    return copy_node.result();
}

} // namespace vespalib::eval
