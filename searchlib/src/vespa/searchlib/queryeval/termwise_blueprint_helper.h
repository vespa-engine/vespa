// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multisearch.h"
#include "blueprint.h"
#include "unpackinfo.h"
#include "searchiterator.h"

namespace search {
namespace queryeval {

/**
 * Utility used to keep track of which children can be evaluated
 * termwise, which children we need to unpack and how to combine the
 * termwise and non-termwise parts with each other.
 **/
struct TermwiseBlueprintHelper {
    MultiSearch::Children children;
    MultiSearch::Children termwise;
    size_t                first_termwise;
    UnpackInfo            termwise_unpack;

    TermwiseBlueprintHelper(const IntermediateBlueprint &self,
                            const MultiSearch::Children &subSearches, UnpackInfo &unpackInfo);
    ~TermwiseBlueprintHelper();

    void insert_termwise(SearchIterator::UP search, bool strict);
};

} // namespace queryeval
} // namespace search
