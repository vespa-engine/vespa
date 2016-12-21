// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "intermediate.h"

namespace search {
namespace query {

Intermediate::~Intermediate() {
    for (size_t i = 0; i < _children.size(); ++i) {
        delete _children[i];
    }
}

Intermediate &Intermediate::append(Node::UP child)
{
    _children.push_back(child.release());
    return *this;
}

}  // namespace query
}  // namespace search
