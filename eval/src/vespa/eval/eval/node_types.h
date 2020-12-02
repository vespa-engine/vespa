// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value_type.h"
#include <map>

namespace vespalib {
namespace eval {

namespace nodes { struct Node; }
class Function;

/**
 * Class keeping track of the output type of all intermediate
 * calculations for a single function. The constructor performs type
 * resolution for each node in the AST based on the type of all
 * function parameters. The default constructor creates an empty type
 * repo where all lookups will result in error types.
 **/
class NodeTypes
{
private:
    ValueType _not_found;
    std::map<const nodes::Node*,ValueType> _type_map;
    std::vector<vespalib::string> _errors;
public:
    NodeTypes();
    NodeTypes(NodeTypes &&rhs) = default;
    NodeTypes &operator=(NodeTypes &&rhs) = default;
    NodeTypes(const nodes::Node &const_node);
    NodeTypes(const Function &function, const std::vector<ValueType> &input_types);
    ~NodeTypes();
    const std::vector<vespalib::string> &errors() const { return _errors; }
    NodeTypes export_types(const nodes::Node &root) const;
    const ValueType &get_type(const nodes::Node &node) const;
    template <typename F>
    void each(F &&f) const {
        for (const auto &entry: _type_map) {
            f(*entry.first, entry.second);
        }
    }
    bool all_types_are_double() const {
        bool all_double = true;
        each([&all_double](const nodes::Node &, const ValueType &type)
             {
                 all_double &= type.is_double();
             });
        return (all_double && (_type_map.size() > 0));
    }
};

} // namespace vespalib::eval
} // namespace vespalib
