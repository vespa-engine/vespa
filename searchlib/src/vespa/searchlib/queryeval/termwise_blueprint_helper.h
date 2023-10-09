// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multisearch.h"
#include "blueprint.h"
#include "unpackinfo.h"
#include "searchiterator.h"

namespace search::queryeval {

/**
 * Utility used to keep track of which children can be evaluated
 * termwise, which children we need to unpack and how to combine the
 * termwise and non-termwise parts with each other.
 **/
struct TermwiseBlueprintHelper {
private:
    MultiSearch::Children termwise_ch;
    MultiSearch::Children other_ch;
public:
    size_t                first_termwise;
    UnpackInfo            termwise_unpack;

    MultiSearch::Children get_termwise_children() { return std::move(termwise_ch); }
    MultiSearch::Children get_result() { return std::move(other_ch); }

    TermwiseBlueprintHelper(const IntermediateBlueprint &self,
                            MultiSearch::Children subSearches, UnpackInfo &unpackInfo);
    ~TermwiseBlueprintHelper();

    void insert_termwise(SearchIterator::UP search, bool strict);
};

}
