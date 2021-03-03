// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "check_type.h"
#include "node_traverser.h"
#include "node_types.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/classname.h>

using vespalib::make_string_short::fmt;

namespace vespalib::eval {
namespace nodes {
namespace {

class State
{
private:
    const std::vector<ValueType>      &_params;
    std::map<const Node *, ValueType> &_type_map;
    std::vector<vespalib::string>     &_errors;

public:
    State(const std::vector<ValueType> &params,
          std::map<const Node *, ValueType> &type_map,
          std::vector<vespalib::string> &errors)
        : _params(params), _type_map(type_map), _errors(errors) {}

    const ValueType &param_type(size_t idx) {
        assert(idx < _params.size());
        return _params[idx];
    }
    void bind(const ValueType &type, const Node &node) {
        auto pos = _type_map.find(&node);
        assert(pos == _type_map.end());
        _type_map.emplace(&node, type);
    }
    const ValueType &type(const Node &node) {
        auto pos = _type_map.find(&node);
        assert(pos != _type_map.end());
        return pos->second;
    }
    void add_error(const vespalib::string &msg) {
        _errors.push_back(msg);
    }
};

struct TypeResolver : public NodeVisitor, public NodeTraverser {
    State state;
    TypeResolver(const std::vector<ValueType> &params_in,
                 std::map<const Node *, ValueType> &type_map_out,
                 std::vector<vespalib::string> &errors_out);
    ~TypeResolver();

    const ValueType &param_type(size_t idx) {
        return state.param_type(idx);
    }

    void fail(const Node &node, const vespalib::string &msg, bool child_types = true) {
        auto str = fmt("%s: %s", vespalib::getClassName(node).c_str(), msg.c_str());
        if (child_types) {
            str += ", child types: [";
            for (size_t i = 0; i < node.num_children(); ++i) {
                if (i > 0) {
                    str += ", ";
                }
                str += state.type(node.get_child(i)).to_spec();
            }
            str += "]";
        }
        state.add_error(str);
        state.bind(ValueType::error_type(), node);
    }

    void bind(const ValueType &type, const Node &node, bool check_error = true) {
        if (check_error && type.is_error()) {
            fail(node, "type resolving failed");
        } else {
            state.bind(type, node);
        }
    }

    const ValueType &type(const Node &node) {
        return state.type(node);
    }

    void import_errors(const NodeTypes &types) {
        for (const auto &err: types.errors()) {
            state.add_error(fmt("[lambda]: %s", err.c_str()));
        }
    }

    void import_types(const NodeTypes &types) {
        types.each([&](const Node &node, const ValueType &type)
                   {
                       state.bind(type, node);
                   });
    }

    //-------------------------------------------------------------------------

    bool check_error(const Node &node) {
        for (size_t i = 0; i < node.num_children(); ++i) {
            if (type(node.get_child(i)).is_error()) {
                bind(ValueType::error_type(), node, false);
                return true;
            }
        }
        return false;
    }

    void resolve_op1(const Node &node) {
        bind(type(node.get_child(0)).map(), node);
    }

    void resolve_op2(const Node &node) {
        bind(ValueType::join(type(node.get_child(0)),
                             type(node.get_child(1))), node);
    }

    //-------------------------------------------------------------------------

    void visit(const Number &node) override {
        bind(ValueType::double_type(), node);
    }
    void visit(const Symbol &node) override {
        bind(param_type(node.id()), node, false);
    }
    void visit(const String &node) override {
        bind(ValueType::double_type(), node);
    }
    void visit(const In &node) override { resolve_op1(node); }
    void visit(const Neg &node) override { resolve_op1(node); }
    void visit(const Not &node) override { resolve_op1(node); }
    void visit(const If &node) override {
        bind(ValueType::either(type(node.true_expr()),
                               type(node.false_expr())), node);
    }
    void visit(const Error &node) override {
        bind(ValueType::error_type(), node, false);
    }
    void visit(const TensorMap &node) override { resolve_op1(node); }
    void visit(const TensorJoin &node) override { resolve_op2(node); }
    void visit(const TensorMerge &node) override {
        bind(ValueType::merge(type(node.get_child(0)),
                              type(node.get_child(1))), node);
    }
    void visit(const TensorReduce &node) override {
        auto my_type = type(node.get_child(0)).reduce(node.dimensions());
        if (my_type.is_error()) {
            auto str = fmt("aggr: %s, dimensions: [",
                           AggrNames::name_of(node.aggr())->c_str());
            size_t i = 0;
            for (const auto &dimension: node.dimensions()) {
                if (i++ > 0) {
                    str += ",";
                }
                str += dimension;
            }
            str += "]";
            fail(node, str);
        } else {
            bind(my_type, node);
        }
    }
    void visit(const TensorRename &node) override {
        auto my_type = type(node.get_child(0)).rename(node.from(), node.to());
        if (my_type.is_error()) {
            auto str = fmt("%s -> %s",
                           TensorRename::flatten(node.from()).c_str(),
                           TensorRename::flatten(node.to()).c_str());
            fail(node, str);
        } else {
            bind(my_type, node);
        }
    }
    void visit(const TensorConcat &node) override {
        bind(ValueType::concat(type(node.get_child(0)),
                               type(node.get_child(1)), node.dimension()), node);
    }
    void visit(const TensorCellCast &node) override {
        bind(type(node.get_child(0)).cell_cast(node.cell_type()), node);
    }
    void visit(const TensorCreate &node) override {
        for (size_t i = 0; i < node.num_children(); ++i) {
            if (!type(node.get_child(i)).is_double()) {
                return fail(node, fmt("non-double child at index %zu", i), false);
            }
        }
        bind(node.type(), node);
    }
    void visit(const TensorLambda &node) override {
        std::vector<ValueType> arg_types;
        for (const auto &dim: node.type().dimensions()) {
            (void) dim;
            arg_types.push_back(ValueType::double_type());
        }
        for (size_t binding: node.bindings()) {
            arg_types.push_back(param_type(binding));
        }
        NodeTypes lambda_types(node.lambda(), arg_types);
        const ValueType &lambda_type = lambda_types.get_type(node.lambda().root());
        if (!lambda_type.is_double()) {
            import_errors(lambda_types);
            return fail(node, fmt("lambda function has non-double result type: %s",
                                  lambda_type.to_spec().c_str()), false);
        }
        import_types(lambda_types);
        bind(node.type(), node);
    }
    void visit(const TensorPeek &node) override {
        const ValueType &param_type = type(node.param());
        std::vector<vespalib::string> dimensions;
        for (const auto &dim: node.dim_list()) {
            dimensions.push_back(dim.first);
            if (dim.second.is_expr()) {
                if (!type(*dim.second.expr).is_double()) {
                    return fail(node, fmt("non-double label expression for dimension %s", dim.first.c_str()));
                }
            } else {
                size_t dim_idx = param_type.dimension_index(dim.first);
                if (dim_idx == ValueType::Dimension::npos) {
                    return fail(node, fmt("dimension not in param: %s", dim.first.c_str()));
                }
                const auto &param_dim = param_type.dimensions()[dim_idx];
                if (param_dim.is_indexed()) {
                    if (!is_number(dim.second.label)) {
                        return fail(node, fmt("non-numeric label for dimension %s: '%s'",
                                        dim.first.c_str(), dim.second.label.c_str()));
                    }
                    if (as_number(dim.second.label) >= param_dim.size) {
                        return fail(node, fmt("out-of-bounds label for dimension %s: %s",
                                        dim.first.c_str(), dim.second.label.c_str()));
                    }
                }
            }
        }
        bind(param_type.peek(dimensions), node);
    }
    void visit(const Add &node) override { resolve_op2(node); }
    void visit(const Sub &node) override { resolve_op2(node); }
    void visit(const Mul &node) override { resolve_op2(node); }
    void visit(const Div &node) override { resolve_op2(node); }
    void visit(const Mod &node) override { resolve_op2(node); }
    void visit(const Pow &node) override { resolve_op2(node); }
    void visit(const Equal &node) override { resolve_op2(node); }
    void visit(const NotEqual &node) override { resolve_op2(node); }
    void visit(const Approx &node) override { resolve_op2(node); }
    void visit(const Less &node) override { resolve_op2(node); }
    void visit(const LessEqual &node) override { resolve_op2(node); }
    void visit(const Greater &node) override { resolve_op2(node); }
    void visit(const GreaterEqual &node) override { resolve_op2(node); }
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
    void visit(const Elu &node) override { resolve_op1(node); }
    void visit(const Erf &node) override { resolve_op1(node); }

    //-------------------------------------------------------------------------

    bool open(const Node &) override {
        return true;
    }

    void close(const Node &node) override {
        if (!check_error(node)) {
            node.accept(*this);
        }
    }
};

TypeResolver::TypeResolver(const std::vector<ValueType> &params_in,
                           std::map<const Node *, ValueType> &type_map_out,
                           std::vector<vespalib::string> &errors_out)
    : state(params_in, type_map_out, errors_out)
{
}

TypeResolver::~TypeResolver() {}

struct TypeExporter : public NodeTraverser {
    const std::map<const Node *, ValueType> &parent_type_map;
    std::map<const Node *, ValueType> &exported_type_map;
    size_t missing_cnt;
    TypeExporter(const std::map<const Node *, ValueType> &parent_type_map_in,
                 std::map<const Node *, ValueType> &exported_type_map_out)
        : parent_type_map(parent_type_map_in),
          exported_type_map(exported_type_map_out),
          missing_cnt(0) {}
    bool open(const Node &node) override {
        if (auto lambda = as<TensorLambda>(node)) {
            lambda->lambda().root().traverse(*this);
        }
        return true;
    }
    void close(const Node &node) override {
        auto pos = parent_type_map.find(&node);
        if (pos != parent_type_map.end()) {
            exported_type_map.emplace(&node, pos->second);
        } else {
            ++missing_cnt;
        }
    }
};

} // namespace vespalib::eval::nodes::<unnamed>
} // namespace vespalib::eval::nodes

NodeTypes::NodeTypes()
    : _not_found(ValueType::error_type()),
      _type_map()
{
}

NodeTypes::NodeTypes(const nodes::Node &const_node)
    : _not_found(ValueType::error_type()),
      _type_map()
{
    std::vector<ValueType> no_input_types;
    nodes::TypeResolver resolver(no_input_types, _type_map, _errors);
    const_node.traverse(resolver);
}

NodeTypes::NodeTypes(const Function &function, const std::vector<ValueType> &input_types)
    : _not_found(ValueType::error_type()),
      _type_map()
{
    assert(input_types.size() == function.num_params());
    nodes::TypeResolver resolver(input_types, _type_map, _errors);
    function.root().traverse(resolver);
}

NodeTypes::~NodeTypes() = default;

NodeTypes
NodeTypes::export_types(const nodes::Node &root) const
{
    NodeTypes exported_types;
    nodes::TypeExporter exporter(_type_map, exported_types._type_map);
    root.traverse(exporter);
    if (exporter.missing_cnt > 0) {
        exported_types._errors.push_back(fmt("[export]: %zu nodes had missing types", exporter.missing_cnt));
    }
    return exported_types;
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

}
