// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visit_stuff.h"
#include "tensor_function.h"
#include "aggr.h"
#include "operation.h"
#include "tensor_nodes.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/vespalib/util/classname.h>

namespace vespalib::eval::visit {
namespace {

vespalib::string name_of(map_fun_t fun) {
    if (fun == operation::Neg::f) return "-";
    if (fun == operation::Not::f) return "!";
    if (fun == operation::Cos::f) return "cos";
    if (fun == operation::Sin::f) return "sin";
    if (fun == operation::Tan::f) return "tan";
    if (fun == operation::Cosh::f) return "cosh";
    if (fun == operation::Sinh::f) return "sinh";
    if (fun == operation::Tanh::f) return "tanh";
    if (fun == operation::Acos::f) return "acos";
    if (fun == operation::Asin::f) return "asin";
    if (fun == operation::Atan::f) return "atan";
    if (fun == operation::Exp::f) return "exp";
    if (fun == operation::Log10::f) return "log10";
    if (fun == operation::Log::f) return "log";
    if (fun == operation::Sqrt::f) return "sqrt";
    if (fun == operation::Ceil::f) return "ceil";
    if (fun == operation::Fabs::f) return "fabs";
    if (fun == operation::Floor::f) return "floor";
    if (fun == operation::IsNan::f) return "isnan";
    if (fun == operation::Relu::f) return "relu";
    if (fun == operation::Sigmoid::f) return "sigmoid";
    if (fun == operation::Elu::f) return "elu";
    return "[other map function]";
}

vespalib::string name_of(join_fun_t fun) {
    if (fun == operation::Add::f) return "+";
    if (fun == operation::Sub::f) return "-";
    if (fun == operation::Mul::f) return "*";
    if (fun == operation::Div::f) return "/";
    if (fun == operation::Mod::f) return "%";
    if (fun == operation::Pow::f) return "^";
    if (fun == operation::Equal::f) return "==";
    if (fun == operation::NotEqual::f) return "!=";
    if (fun == operation::Approx::f) return "~";
    if (fun == operation::Less::f) return "<";
    if (fun == operation::LessEqual::f) return "<=";
    if (fun == operation::Greater::f) return ">";
    if (fun == operation::GreaterEqual::f) return ">=";
    if (fun == operation::And::f) return "&&";
    if (fun == operation::Or::f) return "||";
    if (fun == operation::Atan2::f) return "atan2";
    if (fun == operation::Ldexp::f) return "ldexp";
    if (fun == operation::Min::f) return "min";
    if (fun == operation::Max::f) return "max";
    return "[other join function]";
}

} // namespace vespalib::eval::visit::<unnamed>
} // namespace vespalib::eval::visit

void visit(vespalib::ObjectVisitor &visitor, const vespalib::string &name, const vespalib::eval::TensorFunction &value) {
    visitor.openStruct(name, vespalib::getClassName(value));
    {
        value.visit_self(visitor);
        value.visit_children(visitor);
    }
    visitor.closeStruct();
}

void visit(vespalib::ObjectVisitor &visitor, const vespalib::string &name, vespalib::eval::visit::map_fun_t value) {
    visitor.visitString(name, vespalib::eval::visit::name_of(value));
}

void visit(vespalib::ObjectVisitor &visitor, const vespalib::string &name, vespalib::eval::visit::join_fun_t value) {
    visitor.visitString(name, vespalib::eval::visit::name_of(value));
}

void visit(vespalib::ObjectVisitor &visitor, const vespalib::string &name, const vespalib::eval::Aggr &value) {
    if (const vespalib::string *str_ptr = vespalib::eval::AggrNames::name_of(value)) {
        visitor.visitString(name, *str_ptr);
    } else {
        visitor.visitNull(name);
    }
}

void visit(vespalib::ObjectVisitor &visitor, const vespalib::string &name, const vespalib::eval::visit::DimList &value) {
    vespalib::string list = vespalib::eval::nodes::TensorRename::flatten(value.list);
    visitor.visitString(name, list);
}

void visit(vespalib::ObjectVisitor &visitor, const vespalib::string &name, const vespalib::eval::visit::FromTo &value) {
    vespalib::string from = vespalib::eval::nodes::TensorRename::flatten(value.from);
    vespalib::string to = vespalib::eval::nodes::TensorRename::flatten(value.to);
    visitor.visitString(name, vespalib::make_string("%s -> %s", from.c_str(), to.c_str()));
}
