// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "partial_result.h"

namespace proton::matching {

namespace {

bool before(const search::RankedHit &a, const search::RankedHit &b) {
    if (a.getRank() != b.getRank()) {
        return (a.getRank() > b.getRank());
    }
    return (a.getDocId() < b.getDocId());
}

void mergeHits(size_t maxHits,
               std::vector<search::RankedHit> &hits,
               const std::vector<search::RankedHit> &rhs_hits)
{
    std::vector<search::RankedHit> my_hits;
    std::swap(hits, my_hits);
    hits.reserve(maxHits);
    const search::RankedHit *a_pos = my_hits.data();
    const search::RankedHit *a_end = a_pos + my_hits.size();
    const search::RankedHit *b_pos = rhs_hits.data();
    const search::RankedHit *b_end = b_pos + rhs_hits.size();
    while (a_pos < a_end && b_pos < b_end && hits.size() < maxHits) {
        if (before(*a_pos, *b_pos)) {
            hits.push_back(*a_pos++);
        } else {
            hits.push_back(*b_pos++);
        }
    }
    while (a_pos < a_end && hits.size() < maxHits) {
        hits.push_back(*a_pos++);
    }
    while (b_pos < b_end && hits.size() < maxHits) {
        hits.push_back(*b_pos++);
    }
}

bool before(const PartialResult::SortRef &a, uint32_t docid_a,
            const PartialResult::SortRef &b, uint32_t docid_b) {
    int res = memcmp(a.first, b.first, std::min(a.second, b.second));
    if (res != 0) {
        return (res < 0);
    }
    if (a.second != b.second) {
        return (a.second < b.second);
    }
    return (docid_a < docid_b);
}

size_t mergeHits(size_t maxHits,
                 std::vector<search::RankedHit> &hits,
                 std::vector<PartialResult::SortRef> &sortData,
                 const std::vector<search::RankedHit> &rhs_hits,
                 const std::vector<PartialResult::SortRef> &rhs_sortData)
{
    size_t sortDataSize = 0;
    std::vector<search::RankedHit> my_hits;
    std::vector<PartialResult::SortRef> my_sortData;
    std::swap(hits, my_hits);
    std::swap(sortData, my_sortData);
    hits.reserve(maxHits);
    sortData.reserve(maxHits);
    const search::RankedHit *a_pos = my_hits.data();
    const search::RankedHit *a_end = a_pos + my_hits.size();
    const search::RankedHit *b_pos = rhs_hits.data();
    const search::RankedHit *b_end = b_pos + rhs_hits.size();
    const PartialResult::SortRef *a_sort_pos = my_sortData.data();
    const PartialResult::SortRef *b_sort_pos = rhs_sortData.data();
    while (a_pos < a_end && b_pos < b_end && hits.size() < maxHits) {
        if (before(*a_sort_pos, a_pos->_docId,
                   *b_sort_pos, b_pos->_docId))
        {
            hits.push_back(*a_pos++);
            sortDataSize += a_sort_pos->second;
            sortData.push_back(*a_sort_pos++);
        } else {
            hits.push_back(*b_pos++);
            sortDataSize += b_sort_pos->second;
            sortData.push_back(*b_sort_pos++);
        }
    }
    while (a_pos < a_end && hits.size() < maxHits) {
        hits.push_back(*a_pos++);
        sortDataSize += a_sort_pos->second;
        sortData.push_back(*a_sort_pos++);
    }
    while (b_pos < b_end && hits.size() < maxHits) {
        hits.push_back(*b_pos++);
        sortDataSize += b_sort_pos->second;
        sortData.push_back(*b_sort_pos++);
    }
    return sortDataSize;
}

} // namespace proton::matching::<unnamed>

PartialResult::PartialResult(size_t maxSize_in, bool hasSortData_in)
    : _hits(),
      _sortData(),
      _maxSize(maxSize_in),
      _totalHits(0),
      _hasSortData(hasSortData_in),
      _sortDataSize(0)
{
    _hits.reserve(_maxSize);
    if (_hasSortData) {
        _sortData.reserve(_maxSize);
    }
}

PartialResult::~PartialResult() = default;

void
PartialResult::merge(Source &rhs)
{
    PartialResult &r = static_cast<PartialResult&>(rhs);
    assert(_hasSortData == r._hasSortData);
    _totalHits += r._totalHits;
    if (_hasSortData) {
        _sortDataSize = mergeHits(_maxSize, _hits, _sortData, r._hits, r._sortData);
    } else {
        mergeHits(_maxSize, _hits, r._hits);
    }
}

}
