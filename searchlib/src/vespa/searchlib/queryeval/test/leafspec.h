// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "searchhistory.h"
#include "trackedsearch.h"
#include <vespa/searchlib/queryeval/fake_search.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <string>

namespace search::queryeval::test {

/**
 * Defines the hits to be returned by a wand-like subsearch and creates a TrackedSearch.
 **/
struct LeafSpec
{
    std::string     name;
    int32_t         weight;
    int32_t         maxWeight;
    FakeResult      result;
    SearchIterator *search;
    LeafSpec(const std::string &n, int32_t w = 100)
        : name(n),
          weight(w),
          maxWeight(std::numeric_limits<int32_t>::min()),
          result(),
          search()
    {}
    ~LeafSpec() {}
    LeafSpec &doc(uint32_t docid) {
        result.doc(docid);
        return *this;
    }
    LeafSpec &doc(uint32_t docid, int32_t w) {
        result.doc(docid);
        result.weight(w);
        result.pos(0);
        maxWeight = std::max(maxWeight, w);
        return *this;
    }
    LeafSpec &itr(SearchIterator *si) {
        search = si;
        return *this;
    }
    SearchIterator *create(SearchHistory &hist, fef::TermFieldMatchData *tfmd) const {
        if (search != nullptr) {
            return new TrackedSearch(name, hist, search);
        } else if (tfmd != nullptr) {
            return new TrackedSearch(name, hist, result, *tfmd,
                                     MinMaxPostingInfo(0, maxWeight));
        }
        return new TrackedSearch(name, hist, result,
                                 MinMaxPostingInfo(0, maxWeight));
    }
};

}
