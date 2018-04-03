// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>

namespace search::query { class Node; }
namespace search::fef { class ITermData; }

namespace proton::matching {

struct TermDataExtractor {
    /**
     * Extracts pointers to all TermData objects stored in the term
     * nodes of the node tree.
     */
    static void extractTerms(const search::query::Node &node,
                             std::vector<const search::fef::ITermData *> &td);
};

}
