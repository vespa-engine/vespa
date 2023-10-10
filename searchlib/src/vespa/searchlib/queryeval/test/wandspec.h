// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "leafspec.h"
#include "trackedsearch.h"
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/queryeval/fake_search.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/queryeval/wand/wand_parts.h>
#include <vector>

namespace search::queryeval::test {

/**
 * Defines the overall behavior of a wand like search with tracked children.
 * This struct also owns the search iterator history.
 **/
class WandSpec
{
private:
    std::vector<LeafSpec>             _leafs;
    fef::MatchDataLayout              _layout;
    std::vector<fef::TermFieldHandle> _handles;
    SearchHistory                     _history;

public:
    WandSpec() : _leafs(), _layout(), _handles(), _history() {}
    ~WandSpec() {}
    WandSpec &leaf(LeafSpec && l) {
        _leafs.emplace_back(std::move(l));
        _handles.push_back(_layout.allocTermField(0));
        return *this;
    }
    wand::Terms getTerms(fef::MatchData *matchData = NULL) {
        wand::Terms terms;
        for (size_t i = 0; i < _leafs.size(); ++i) {
            fef::TermFieldMatchData *tfmd = (matchData != NULL ? matchData->resolveTermField(_handles[i]) : NULL);
            terms.push_back(wand::Term(_leafs[i].create(_history, tfmd).release(),
                                       _leafs[i].weight,
                                       _leafs[i].result.inspect().size(),
                                       tfmd));
        }
        return terms;
    }
    SearchHistory &getHistory() { return _history; }
    fef::MatchData::UP createMatchData() const { return _layout.createMatchData(); }
};

}
