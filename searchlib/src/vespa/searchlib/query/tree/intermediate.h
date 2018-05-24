// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <vespa/searchlib/query/tree/node.h>

namespace search::query {

class Intermediate : public Node
{
    std::vector<Node *> _children;
 public:
    typedef std::unique_ptr<Intermediate> UP;

    Intermediate(const Intermediate & rhs) = delete;
    Intermediate & operator = (const Intermediate & rhs) = delete;

    Intermediate() = default;
    virtual ~Intermediate() = 0;

    const std::vector<Node *> &getChildren() const { return _children; }
    Intermediate &reserve(size_t sz) { _children.reserve(sz); return *this; }
    Intermediate &append(Node::UP child);
};

}
