// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace vespalib::eval {

namespace nodes { struct Node; }

struct NodeTools {
    static size_t min_num_params(const nodes::Node &node);
    static std::unique_ptr<nodes::Node> copy(const nodes::Node &node);
};

} // namespace vespalib::eval
