// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value_type.h"
#include <map>

namespace vespalib {
namespace eval {

namespace nodes { class Node; }
class Function;

/**
 * Class keeping track of the output type of all intermediate
 * calculations for a single function. The constructor performs type
 * resolution for each node in the AST based on the type of all
 * function parameters. The default constructor creates an empty type
 * repo representing an unknown number of unknown values.
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
    bool all_types_are_double() const;
};

} // namespace vespalib::eval
} // namespace vespalib
