// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "node_visitor.h"

namespace vespalib {
namespace eval {
namespace nodes {

/**
 * A templated visitor used to check if the visited node matches any
 * of the given types.
 **/

template <typename... TYPES> struct CheckTypeVisitor;

template <>
struct CheckTypeVisitor<> : EmptyNodeVisitor {
    bool result = false;
};

template <typename HEAD, typename... TAIL>
struct CheckTypeVisitor<HEAD, TAIL...> : CheckTypeVisitor<TAIL...> {
    virtual void visit(const HEAD &) override { this->result = true; }
};

template <typename... TYPES>
bool check_type(const nodes::Node &node) {
    CheckTypeVisitor<TYPES...> check;
    node.accept(check);
    return check.result;
}

} // namespace vespalib::eval::nodes
} // namespace vespalib::eval
} // namespace vespalib
