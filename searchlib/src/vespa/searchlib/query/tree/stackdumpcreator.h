// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::query {

class Node;

struct StackDumpCreator {
    // Creates a stack dump from a query tree.
    static vespalib::string create(const Node &node);
};

}
