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
public:
    NodeTypes();
    NodeTypes(const Function &function, const std::vector<ValueType> &input_types);
    const ValueType &get_type(const nodes::Node &node) const;
    template <typename P>
    bool check_types(const P &pred) const {
        for (const auto &entry: _type_map) {
            if (!pred(entry.second)) {
                return false;
            }
        }
        return (_type_map.size() > 0);
    }
    bool all_types_are_double() const {
        return check_types([](const ValueType &type)
                           { return type.is_double(); });
    }
};

} // namespace vespalib::eval
} // namespace vespalib
