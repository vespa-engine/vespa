// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "basic_nodes.h"
#include "node_traverser.h"
#include "node_visitor.h"
#include "interpreted_function.h"
#include "simple_value.h"
#include "fast_value.h"
#include "node_tools.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace vespalib::eval::nodes {

namespace {

struct Frame {
    const Node &node;
    size_t child_idx;
    explicit Frame(const Node &node_in) noexcept : node(node_in), child_idx(0) {}
    bool has_next_child() const { return (child_idx < node.num_children()); }
    const Node &next_child() { return node.get_child(child_idx++); }
};

} // namespace vespalib::eval::nodes::<unnamed>

vespalib::string
Number::dump(DumpContext &) const {
    return make_string("%g", _value);
}

vespalib::string
If::dump(DumpContext &ctx) const {
    vespalib::string str;
    str += "if(";
    str += _cond->dump(ctx);
    str += ",";
    str += _true_expr->dump(ctx);
    str += ",";
    str += _false_expr->dump(ctx);
    if (_p_true != 0.5) {
        str += make_string(",%g", _p_true);
    }
    str += ")";
    return str;
}
double
Node::get_const_double_value() const
{
    assert(is_const_double());
    NodeTypes node_types(*this);
    InterpretedFunction function(SimpleValueBuilderFactory::get(), *this, node_types);
    NoParams no_params;
    InterpretedFunction::Context ctx(function);
    return function.eval(ctx, no_params).as_double();
}

Value::UP
Node::get_const_value() const
{
    if (nodes::as<nodes::Error>(*this)) {
        // cannot get const value for parse error
        return {nullptr};
    }
    if (NodeTools::min_num_params(*this) != 0) {
        // cannot get const value for non-const sub-expression
        return {nullptr};
    }
    NodeTypes node_types(*this);
    InterpretedFunction function(SimpleValueBuilderFactory::get(), *this, node_types);
    NoParams no_params;
    InterpretedFunction::Context ctx(function);
    return FastValueBuilderFactory::get().copy(function.eval(ctx, no_params));
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

If::If(Node_UP cond_in, Node_UP true_expr_in, Node_UP false_expr_in, double p_true_in)
    : _cond(std::move(cond_in)),
      _true_expr(std::move(true_expr_in)),
      _false_expr(std::move(false_expr_in)),
      _p_true(p_true_in),
      _is_tree(false)
{
    auto less = as<Less>(cond());
    auto in = as<In>(cond());
    auto inverted = as<Not>(cond());
    bool true_is_subtree = (true_expr().is_tree() || true_expr().is_const_double());
    bool false_is_subtree = (false_expr().is_tree() || false_expr().is_const_double());
    if (true_is_subtree && false_is_subtree) {
        if (less) {
            _is_tree = (less->lhs().is_param() && less->rhs().is_const_double());
        } else if (in) {
            _is_tree = in->child().is_param();
        } else if (inverted) {
            if (auto ge = as<GreaterEqual>(inverted->child())) {
                _is_tree = (ge->lhs().is_param() && ge->rhs().is_const_double());
            }
        }
    }
}

}
