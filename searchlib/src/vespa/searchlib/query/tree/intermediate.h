// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "node.h"
#include <vector>

namespace search::query {

class Intermediate : public Node
{
    std::vector<Node *> _children;
 public:
    using UP = std::unique_ptr<Intermediate>;

    Intermediate(const Intermediate & rhs) = delete;
    Intermediate & operator = (const Intermediate & rhs) = delete;

    Intermediate() = default;
    virtual ~Intermediate() = 0;
    bool isIntermediate() const override { return true; }

    const std::vector<Node *> &getChildren() const { return _children; }
    Intermediate &reserve(size_t sz) { _children.reserve(sz); return *this; }
    Intermediate &prepend(Node::UP child);
    Intermediate &append(Node::UP child);
    Node::UP stealFirst();
};

}
