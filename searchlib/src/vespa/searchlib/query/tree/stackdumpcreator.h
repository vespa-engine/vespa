// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <string>

namespace search {
    class SerializedQueryTree;
    using QueryTreeSP = std::shared_ptr<const SerializedQueryTree>;
}

namespace search::query {

class Node;

struct StackDumpCreator {
    // Creates a stack dump from a query tree.
    static std::string create(const Node &node);
    // Creates a SerializedQueryTree from a query tree.
    static search::QueryTreeSP createQueryTree(const Node &node);
};

}
