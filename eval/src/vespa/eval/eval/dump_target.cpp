// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dump_target.h"
#include "tensor_function.h"
#include "aggr.h"
#include "operation.h"

namespace vespalib {
namespace eval {

namespace {

using map_fun_t = double (*)(double);
using join_fun_t = double (*)(double, double);

vespalib::string
name_of(map_fun_t fun)
{
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

vespalib::string
name_of(join_fun_t fun)
{
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

}  // namespace vespalib::eval::<unnamed>

struct DumpTargetBackend {
public:
    asciistream stream;
    asciistream &indent(size_t level) {
        stream << "\n";
        stream << asciistream::Width(level*2) << "";
        return stream;
    }
};

DumpTarget::DumpTarget(DumpTargetBackend &back_end)
  : _back_end(back_end), _indentLevel(0), _nodeName("root")
{}

DumpTarget::DumpTarget(DumpTargetBackend &back_end, size_t level)
  : _back_end(back_end), _indentLevel(level), _nodeName("child")
{}

void
DumpTarget::indent()
{
    _back_end.indent(_indentLevel);
}

vespalib::string
DumpTarget::dump(const TensorFunction &root)
{
    DumpTargetBackend back_end;
    back_end.stream << "root type: " << root.result_type().to_spec();
    DumpTarget target(back_end);
    root.dump_tree(target);
    back_end.stream << "\n";
    return back_end.stream.str();
}

void
DumpTarget::node(const vespalib::string &name)
{
    _nodeName = name;
    indent();
    _back_end.stream << "node name='" << name << "'";
}

void
DumpTarget::child(const vespalib::string &name, const TensorFunction &child)
{
    indent();
    _back_end.stream << _nodeName << " child name='" << name
        << "' type: " << child.result_type().to_spec();
    DumpTarget nextLevel(_back_end, _indentLevel + 1);
    child.dump_tree(nextLevel);
}

DumpTarget::Arg::Arg(DumpTargetBackend &back_end)
  : _back_end(back_end)
{}

void DumpTarget::Arg::value(bool v) { _back_end.stream << (v ? "true" : "false"); }
void DumpTarget::Arg::value(size_t v) { _back_end.stream << v; }
void DumpTarget::Arg::value(map_fun_t v) { value(name_of(v)); }
void DumpTarget::Arg::value(join_fun_t v) { value(name_of(v)); }
void DumpTarget::Arg::value(const vespalib::string &v) { _back_end.stream << "'" << v << "'"; }
void DumpTarget::Arg::value(const std::vector<vespalib::string> &v) {
    _back_end.stream << "[";
    for (size_t i = 0; i < v.size(); ++i) {
        if (i > 0) { _back_end.stream << ", "; }
        _back_end.stream << "'" << v[i] << "'";
    }
    _back_end.stream << "]";
}
void DumpTarget::Arg::value(const Aggr &aggr) { value(*AggrNames::name_of(aggr)); }

DumpTarget::Arg
DumpTarget::arg(const vespalib::string &name)
{
    indent();
    _back_end.stream << _nodeName << " arg name='" << name << "' value=";
    return Arg(_back_end);
}

DumpTarget::~DumpTarget()
{
}

} // namespace vespalib::eval
} // namespace vespalib
