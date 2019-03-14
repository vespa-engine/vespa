// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multisearch.h"

namespace search::queryeval {

/**
 * A simple implementation of the Rank search operation.
 **/
class RankSearch : public MultiSearch
{
protected:
    void doSeek(uint32_t docid) override;

    /**
     * Create a new Rank Search with the given children. A non-strict Rank has
     * no strictness assumptions about its children.
     *
     * @param children the search objects we are rank'ing
     **/
    RankSearch(const Children & children) : MultiSearch(children) { }

public:
    // Caller takes ownership of the returned SearchIterator.
    static SearchIterator *create(const Children &children, bool strict);
};

}
