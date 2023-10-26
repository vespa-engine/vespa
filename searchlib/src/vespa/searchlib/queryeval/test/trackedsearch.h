// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "searchhistory.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/queryeval/fake_search.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <string>

namespace search::queryeval::test {

/**
 * Proxy and wrapper for FakeSearch to track search history and
 * keep match data.
 **/
class TrackedSearch : public SearchIterator
{
private:
    std::string                          _name;
    SearchHistory                       &_history;
    fef::TermFieldMatchData              _matchData;
    SearchIterator::UP                   _search;
    std::unique_ptr<MinMaxPostingInfo>   _minMaxPostingInfo;

    static fef::TermFieldMatchDataArray makeArray(fef::TermFieldMatchData &match) {
        fef::TermFieldMatchDataArray array;
        array.add(&match);
        return array;
    }

protected:
    void doSeek(uint32_t docid) override {
        _history.seek(_name, docid);
        _search->seek(docid);
        setDocId(_search->getDocId());
        _history.step(_name, getDocId());
    }
    void doUnpack(uint32_t docid) override {
        _history.unpack(_name, docid);
        _search->unpack(docid);
    }

public:
    // wraps a FakeSearch and owns its match data
    TrackedSearch(const std::string &name, SearchHistory &hist,
                  const FakeResult &result, const MinMaxPostingInfo &minMaxPostingInfo)
        : _name(name), _history(hist), _matchData(),
          _search(new FakeSearch("<tag>", "<field>", "<term>", result, makeArray(_matchData))),
          _minMaxPostingInfo(new MinMaxPostingInfo(minMaxPostingInfo))
    { setDocId(_search->getDocId()); }
    // wraps a FakeSearch with external match data
    TrackedSearch(const std::string &name, SearchHistory &hist,
                  const FakeResult &result, fef::TermFieldMatchData &tfmd,
                  const MinMaxPostingInfo &minMaxPostingInfo)
        : _name(name), _history(hist), _matchData(),
          _search(new FakeSearch("<tag>", "<field>", "<term>", result, makeArray(tfmd))),
          _minMaxPostingInfo(new MinMaxPostingInfo(minMaxPostingInfo))
    { setDocId(_search->getDocId()); }

    // wraps a generic search (typically wand)
    TrackedSearch(const std::string &name, SearchHistory &hist, SearchIterator::UP search)
        : _name(name), _history(hist), _matchData(), _search(std::move(search)), _minMaxPostingInfo()
    { setDocId(_search->getDocId()); }

    // wraps a generic search (typically wand)
    TrackedSearch(const std::string &name, SearchHistory &hist, SearchIterator *search)
        : _name(name), _history(hist), _matchData(), _search(search), _minMaxPostingInfo()
    { setDocId(_search->getDocId()); }

    const PostingInfo *getPostingInfo() const override {
        return _minMaxPostingInfo.get();
    }
};

}
