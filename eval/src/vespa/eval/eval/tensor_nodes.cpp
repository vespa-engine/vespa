// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_nodes.h"
#include "node_visitor.h"

namespace vespalib::eval::nodes {

void TensorMap         ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorMapSubspaces::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorJoin        ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorMerge       ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorReduce      ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorRename      ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorConcat      ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorCellCast    ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorCreate      ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorLambda      ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorPeek        ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }

vespalib::string
TensorMap::dump(DumpContext &ctx) const {
    vespalib::string str;
    str += "map(";
    str += _child->dump(ctx);
    str += ",";
    str += _lambda->dump_as_lambda();
    str += ")";
    return str;
}

vespalib::string
TensorMapSubspaces::dump(DumpContext &ctx) const {
    vespalib::string str;
    str += "map_subspaces(";
    str += _child->dump(ctx);
    str += ",";
    str += _lambda->dump_as_lambda();
    str += ")";
    return str;
}

vespalib::string
TensorJoin::dump(DumpContext &ctx) const {
    vespalib::string str;
    str += "join(";
    str += _lhs->dump(ctx);
    str += ",";
    str += _rhs->dump(ctx);
    str += ",";
    str += _lambda->dump_as_lambda();
    str += ")";
    return str;
}

vespalib::string
TensorMerge::dump(DumpContext &ctx) const {
    vespalib::string str;
    str += "join(";
    str += _lhs->dump(ctx);
    str += ",";
    str += _rhs->dump(ctx);
    str += ",";
    str += _lambda->dump_as_lambda();
    str += ")";
    return str;
}

vespalib::string
TensorReduce::dump(DumpContext &ctx) const {
    vespalib::string str;
    str += "reduce(";
    str += _child->dump(ctx);
    str += ",";
    str += *AggrNames::name_of(_aggr);
    for (const auto &dimension: _dimensions) {
        str += ",";
        str += dimension;
    }
    str += ")";
    return str;
}

vespalib::string
TensorRename::dump(DumpContext &ctx) const  {
    vespalib::string str;
    str += "rename(";
    str += _child->dump(ctx);
    str += ",";
    str += flatten(_from);
    str += ",";
    str += flatten(_to);
    str += ")";
    return str;
}

vespalib::string
TensorRename::flatten(const std::vector<vespalib::string> &list) {
    if (list.size() == 1) {
        return list[0];
    }
    vespalib::string str = "(";
    for (size_t i = 0; i < list.size(); ++i) {
        if (i > 0) {
            str += ",";
        }
        str += list[i];
    }
    str += ")";
    return str;
}

vespalib::string
TensorConcat::dump(DumpContext &ctx) const {
    vespalib::string str;
    str += "concat(";
    str += _lhs->dump(ctx);
    str += ",";
    str += _rhs->dump(ctx);
    str += ",";
    str += _dimension;
    str += ")";
    return str;
}

vespalib::string
TensorCellCast::dump(DumpContext &ctx) const {
    vespalib::string str;
    str += "cell_cast(";
    str += _child->dump(ctx);
    str += ",";
    str += value_type::cell_type_to_name(_cell_type);
    str += ")";
    return str;
}

vespalib::string
TensorCreate::dump(DumpContext &ctx) const {
    vespalib::string str = _type.to_spec();
    str += ":{";
    CommaTracker child_list;
    for (const Child &child: _cells) {
        child_list.maybe_add_comma(str);
        str += as_string(child.first);
        str += ":";
        str += child.second->dump(ctx);
    }
    str += "}";
    return str;
}

vespalib::string
TensorLambda::dump(DumpContext &) const {
    vespalib::string str = _type.to_spec();
    vespalib::string expr = _lambda->dump();
    if (starts_with(expr, "(")) {
        str += expr;
    } else {
        str += "(";
        str += expr;
        str += ")";
    }
    return str;
}

vespalib::string
TensorPeek::dump(DumpContext &ctx) const {
    vespalib::string str = _param->dump(ctx);
    str += "{";
    CommaTracker dim_list;
    for (const auto &dim : _dim_list) {
        dim_list.maybe_add_comma(str);
        str += dim.first;
        str += ":";
        if (dim.second.is_expr()) {
            vespalib::string expr = dim.second.expr->dump(ctx);
            if (starts_with(expr, "(")) {
                str += expr;
            } else {
                str += "(";
                str += expr;
                str += ")";
            }
        } else {
            str += as_quoted_string(dim.second.label);
        }
    }
    str += "}";
    return str;
}

}
