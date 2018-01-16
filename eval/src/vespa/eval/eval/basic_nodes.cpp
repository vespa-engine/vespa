// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "basic_nodes.h"
#include "node_traverser.h"
#include "node_visitor.h"
#include "interpreted_function.h"
#include "simple_tensor_engine.h"

namespace vespalib {
namespace eval {
namespace nodes {

namespace {

struct Frame {
    const Node &node;
    size_t child_idx;
    explicit Frame(const Node &node_in) : node(node_in), child_idx(0) {}
    bool has_next_child() const { return (child_idx < node.num_children()); }
    const Node &next_child() { return node.get_child(child_idx++); }
};

struct NoParams : LazyParams {
    const Value &resolve(size_t, Stash &stash) const override {
        return stash.create<ErrorValue>();
    }
};

} // namespace vespalib::eval::nodes::<unnamed>

double
Node::get_const_value() const {
    assert(is_const());
    InterpretedFunction function(SimpleTensorEngine::ref(), *this, 0, NodeTypes());
    NoParams no_params;
    InterpretedFunction::Context ctx(function);
    return function.eval(ctx, no_params).as_double();
}

void
Node::traverse(NodeTraverser &traverser) const
{
    if (!traverser.open(*this)) {
        return;
    }
    std::vector<Frame> stack({Frame(*this)});
    while (!stack.empty()) {
        if (stack.back().has_next_child()) {
            const Node &next_child = stack.back().next_child();
            if (traverser.open(next_child)) {
                stack.emplace_back(next_child);
            }
        } else {
            traverser.close(stack.back().node);
            stack.pop_back();
        }
    }
}

void Number::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void Symbol::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void String::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void In    ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void Neg   ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void Not   ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void If    ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void Error ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }

vespalib::string
String::dump(DumpContext &) const
{
    vespalib::string str;
    str.push_back('"');
    for (uint32_t i = 0; i < _value.size(); ++i) {
        char c = _value[i];
        switch (c) {
        case '\\':
            str.append("\\\\");
            break;
        case '"':
            str.append("\\\"");
            break;
        case '\t':
            str.append("\\t");
            break;
        case '\n':
            str.append("\\n");
            break;
        case '\r':
            str.append("\\r");
            break;
        case '\f':
            str.append("\\f");
            break;
        default:
            if (static_cast<unsigned char>(c) >= 32 &&
                static_cast<unsigned char>(c) <= 126)
            {
                str.push_back(c);
            } else {
                const char *lookup = "0123456789abcdef";
                str.append("\\x");
                str.push_back(lookup[(c >> 4) & 0xf]);
                str.push_back(lookup[c & 0xf]);
            }
        }
    }
    str.push_back('"');
    return str;
}

If::If(Node_UP cond_in, Node_UP true_expr_in, Node_UP false_expr_in, double p_true_in)
    : _cond(std::move(cond_in)),
      _true_expr(std::move(true_expr_in)),
      _false_expr(std::move(false_expr_in)),
      _p_true(p_true_in),
      _is_tree(false)
{
    auto less = as<Less>(cond());
    auto in = as<In>(cond());
    bool true_is_subtree = (true_expr().is_tree() || true_expr().is_const());
    bool false_is_subtree = (false_expr().is_tree() || false_expr().is_const());
    if (true_is_subtree && false_is_subtree) {
        if (less) {
            _is_tree = (less->lhs().is_param() && less->rhs().is_const());
        } else if (in) {
            _is_tree = in->child().is_param();
        }
    }
}

} // namespace vespalib::eval::nodes
} // namespace vespalib::eval
} // namespace vespalib
