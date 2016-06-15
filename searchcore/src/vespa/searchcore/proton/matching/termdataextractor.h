// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>

namespace search {
namespace query { class Node; }
namespace fef { class ITermData; }
}  // namespace search

namespace proton {
namespace matching {

struct TermDataExtractor {
    /**
     * Extracts pointers to all TermData objects stored in the term
     * nodes of the node tree.
     */
    static void extractTerms(const search::query::Node &node,
                             std::vector<const search::fef::ITermData *> &td);
};

}  // namespace matching
}  // namespace proton

