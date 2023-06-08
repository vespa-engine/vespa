// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/dual_merge_director.h>
#include <vespa/searchlib/common/rankedhit.h>
#include <vector>
#include <cassert>

namespace proton::matching {

/**
 * The best hits from each match thread are put into a partial result
 * and merged with results from other threads.
 **/
class PartialResult : public vespalib::DualMergeDirector::Source
{
public:
    using UP = std::unique_ptr<PartialResult>;
    using SortRef = std::pair<const char *, size_t>;

private:
    std::vector<search::RankedHit> _hits;
    std::vector<SortRef>           _sortData;
    size_t                         _maxSize;
    size_t                         _totalHits;
    bool                           _hasSortData;
    size_t                         _sortDataSize;

public:
    PartialResult(size_t maxSize_in, bool hasSortData_in);
    ~PartialResult() override;
    size_t size() const { return _hits.size(); }
    size_t maxSize() const { return _maxSize; }
    size_t totalHits() const { return _totalHits; }
    bool hasSortData() const { return _hasSortData; }
    size_t sortDataSize() const { return _sortDataSize; }
    const search::RankedHit &hit(size_t i) const { return _hits[i]; }
    const SortRef &sortData(size_t i) const { return _sortData[i]; }
    void totalHits(size_t th) { _totalHits = th; }
    void add(const search::RankedHit &h) {
        assert(!_hasSortData);
        _hits.push_back(h);
    }
    void add(const search::RankedHit &h, const SortRef &sd) {
        assert(_hasSortData);
        _hits.push_back(h);
        _sortData.push_back(sd);
        _sortDataSize += sd.second;
    }
    void merge(Source &rhs) override;
};

}

