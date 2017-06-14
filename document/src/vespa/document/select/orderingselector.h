// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <utility>
#include <memory>
#include "orderingspecification.h"

namespace document {
namespace select {
    class Node;
}

class OrderingSelector {
public:
    /**
     * Return the ordering specification implied by this document selection expression.
     *
     * @param expression The document selection expression to parse.
     * @param ordering The ordering the user has selected to visit (ASCENDING/DESCENDING)
     */
    OrderingSpecification::UP select(const select::Node& expression, OrderingSpecification::Order ordering) const;

private:
};

} // document

