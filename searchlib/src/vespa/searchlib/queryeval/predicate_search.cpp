// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "predicate_search.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <algorithm>

using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
using std::vector;
using namespace search::predicate;

namespace search {
    using predicate::MIN_INTERVAL;
    using predicate::MAX_INTERVAL;
}

namespace search::queryeval {

namespace {

#ifdef __x86_64__
class SkipMinFeatureSSE2 : public SkipMinFeature
{
public:
    SkipMinFeatureSSE2(const uint8_t * min_feature, const uint8_t * kv, size_t sz);
private:
    typedef char v16u8 __attribute__((vector_size(16)));
    uint32_t next() override;
    uint32_t cmp32(size_t j) {
        v16u8 r0 = _kv[j*2] >= _min_feature[j*2];
        v16u8 r1 = _kv[j*2+1] >= _min_feature[j*2+1];
        return __builtin_ia32_pmovmskb128(r0) | (__builtin_ia32_pmovmskb128(r1) << 16);
    }
    void advance();
    const v16u8 * _min_feature;
    const v16u8 * _kv;
    uint32_t _sz;
    uint32_t _chunk;
    uint32_t _last32;
};

SkipMinFeatureSSE2::SkipMinFeatureSSE2(const uint8_t * min_feature, const uint8_t * kv, size_t sz) :
    _min_feature(reinterpret_cast<const v16u8 *>(min_feature)),
    _kv(reinterpret_cast<const v16u8 *>(kv)),
    _sz(sz),
    _chunk(0),
    _last32(0)
{
    advance();
    if (_chunk == 1) {
        _last32 &= ~0x1;
    }
}

void
SkipMinFeatureSSE2::advance()
{
    for (;(_last32 == 0) && (_chunk < (_sz>>5)); _last32 = cmp32(_chunk++));
    if (_last32 == 0) {
        const uint8_t * min_feature = reinterpret_cast<const uint8_t *>(_min_feature);
        const uint8_t * kv = reinterpret_cast<const uint8_t *>(_kv);
        for (size_t i(_chunk << 5); i < _sz; i++) {
            if (kv[i] >= min_feature[i]) {
                _last32 |= 1 << (i - (_chunk << 5));
            }
        }
        _chunk++;
    }
}

uint32_t
SkipMinFeatureSSE2::next()
{
    if (__builtin_expect(_last32 == 0, true)) {
        advance();
    }
    if (_last32) {
        uint32_t n = vespalib::Optimized::lsbIdx(_last32);
        _last32 &= ~(1 << n);
        n += ((_chunk - 1) << 5);
        return n < _sz ? n : -1;
    } else {
        return -1;
    }
}
#else
class SkipMinFeatureGeneric : public SkipMinFeature
{
    const uint8_t* _min_feature;
    const uint8_t* _kv;
    const uint32_t _sz;
    uint32_t       _cur;
public:
    SkipMinFeatureGeneric(const uint8_t* min_feature, const uint8_t* kv, size_t sz);
    uint32_t next() override;
};

SkipMinFeatureGeneric::SkipMinFeatureGeneric(const uint8_t* min_feature, const uint8_t* kv, size_t sz)
    : _min_feature(min_feature),
      _kv(kv),
      _sz(sz),
      _cur(0)
{
}

uint32_t
SkipMinFeatureGeneric::next()
{
    while (_cur < _sz) {
        if (_kv[_cur] >= _min_feature[_cur]) {
            return _cur++;
        }
        ++_cur;
    }
    return -1;
}
#endif

}

SkipMinFeature::UP
SkipMinFeature::create(const uint8_t * min_feature, const uint8_t * kv, size_t sz)
{
#ifdef __x86_64__
    return std::make_unique<SkipMinFeatureSSE2>(min_feature, kv, sz);
#else
    return std::make_unique<SkipMinFeatureGeneric>(min_feature, kv, sz);
#endif
}

PredicateSearch::PredicateSearch(const uint8_t * minFeatureVector,
                                 const IntervalRange * interval_range_vector,
                                 IntervalRange max_interval_range,
                                 CondensedBitVector::CountVector kV,
                                 vector<PredicatePostingList::UP> posting_lists,
                                 const fef::TermFieldMatchDataArray &tfmda)
    : _skip(SkipMinFeature::create(minFeatureVector, &kV[0], kV.size())),
      _posting_lists(std::move(posting_lists)),
      _sorted_indexes(_posting_lists.size()),
      _sorted_indexes_merge_buffer(_posting_lists.size()),
      _doc_ids(_posting_lists.size()),
      _intervals(_posting_lists.size()),
      _subqueries(_posting_lists.size()),
      _subquery_markers(new uint64_t[max_interval_range+1]),
      _visited(new bool[max_interval_range+1]),
      _termFieldMatchData(tfmda.valid()? tfmda[0] : nullptr),
      _min_feature_vector(minFeatureVector),
      _interval_range_vector(interval_range_vector)
{

    for (size_t i = 0; i < _posting_lists.size(); ++i) {
        _sorted_indexes[i] = i;
        _doc_ids[i] = _posting_lists[i]->getDocId();
        _subqueries[i] = _posting_lists[i]->getSubquery();
    }
}

PredicateSearch::~PredicateSearch()
{
    delete [] _visited;
    delete [] _subquery_markers;
}

bool
PredicateSearch::advanceOneTo(uint32_t doc_id, size_t index) {
    size_t i = _sorted_indexes[index];
    if (__builtin_expect(_posting_lists[i]->next(doc_id - 1), true)) {
        _doc_ids[i] = _posting_lists[i]->getDocId();
        return true;
    }
    _doc_ids[i] = UINT32_MAX;  // will be last after sorting.
    return false;
}

namespace {
template <typename CompareType>
void
sort_indexes(uint16_t *indexes, size_t size, CompareType *values) {
    std::sort(indexes, indexes + size,
              [&] (uint16_t a, uint16_t b) { return values[a] < values[b]; });
}
}  // namespace

void
PredicateSearch::advanceAllTo(uint32_t doc_id) {
    size_t i = 0;
    size_t completed_count = 0;
    for (; i < _sorted_indexes.size() && _doc_ids[_sorted_indexes[i]] < doc_id; ++i) {
        if (!advanceOneTo(doc_id, i)) {
            ++completed_count;
        }
    }
    if (__builtin_expect((i > 0) && ! _sorted_indexes.empty(), true)) {
        sort_indexes(&_sorted_indexes[0], i, &_doc_ids[0]);
        std::merge(_sorted_indexes.begin(), _sorted_indexes.begin() + i,
                   _sorted_indexes.begin() + i, _sorted_indexes.end(),
                   _sorted_indexes_merge_buffer.begin(),
                   [&] (uint16_t a, uint16_t b) { return _doc_ids[a] < _doc_ids[b]; });
        _sorted_indexes.swap(_sorted_indexes_merge_buffer);
        // After sorting and merging the completed indexes are at the end.
        _sorted_indexes.resize(_sorted_indexes.size() - completed_count);
        _sorted_indexes_merge_buffer.resize(_sorted_indexes.size());
    }
}


namespace {
bool
isNotInterval(uint32_t begin, uint32_t end) {
    return begin > end;
}

void
markSubquery(uint32_t begin, uint32_t end, uint64_t subquery, uint64_t *subquery_markers, bool * visited) {
    if (visited[begin]) {
        visited[end] = true;
        subquery_markers[end] |= subquery;
    }
}

// Returns the semantic interval end - or UINT32_MAX if no interval cover is possible
uint32_t
addInterval(uint32_t interval, uint64_t subquery, uint64_t *subquery_markers,
            bool * visited, uint32_t highest_end_seen)
{
    uint32_t begin = interval >> 16;
    uint32_t end = interval & 0xffff;

    if (isNotInterval(begin, end)) {
        // Note: End and begin values are swapped for zStar intervals
        if (highest_end_seen < end) return UINT32_MAX;
        markSubquery(end, begin, ~(subquery_markers[end]), subquery_markers, visited);
        return begin;
    } else {
        if (highest_end_seen < begin - 1) return UINT32_MAX;
        markSubquery(begin - 1, end, subquery_markers[begin - 1] & subquery, subquery_markers, visited);
        return end;
    }
}

// One step of insertion sort: First element is moved to correct position.
void
restoreSortedOrder(size_t first, size_t last, vector<uint16_t> &indexes, const vector<uint32_t> &intervals) {
    uint32_t interval_to_move = intervals[indexes[first]];
    uint16_t index_to_move = indexes[first];
    while (++first < last && interval_to_move > intervals[indexes[first]]) {
        indexes[first - 1] = indexes[first];
    }
    indexes[first - 1] = index_to_move;
}

}  // namespace

bool
PredicateSearch::evaluateHit(uint32_t doc_id, uint32_t k) {
    size_t candidates = sortIntervals(doc_id, k);

    size_t interval_end = _interval_range_vector[doc_id];
    memset(_subquery_markers, 0, sizeof(uint64_t) * (interval_end + 1));
    memset(_visited, false, sizeof(bool) * (interval_end + 1));
    _subquery_markers[0] = UINT64_MAX;
    _visited[0] = true;

    uint32_t highest_end_seen = 1;
    for (size_t i = 0; i < candidates; ) {
        size_t index = _sorted_indexes[i];
        uint32_t last_end_seen = addInterval(_intervals[index], _subqueries[index],
                                             _subquery_markers, _visited, highest_end_seen);
        if (last_end_seen == UINT32_MAX) {
            return false;
        }
        highest_end_seen = std::max(last_end_seen, highest_end_seen);
        if (_posting_lists[index]->nextInterval()) {
            _intervals[index] = _posting_lists[index]->getInterval();
            restoreSortedOrder(i, candidates, _sorted_indexes, _intervals);
        } else {
            ++i;
        }
    }
    return _subquery_markers[interval_end] != 0;
}

size_t
PredicateSearch::sortIntervals(uint32_t doc_id, uint32_t k) {
    size_t candidates = k + 1;
    for (size_t i = candidates; i < _sorted_indexes.size(); ++i) {
        if (_doc_ids[_sorted_indexes[i]] == doc_id) {
            ++candidates;
        } else {
            break;
        }
    }
    for (size_t i = 0; i < candidates; i++) {
        _intervals[_sorted_indexes[i]] = _posting_lists[_sorted_indexes[i]]->getInterval();
    }
    sort_indexes(&_sorted_indexes[0], candidates, &_intervals[0]);
    return candidates;
}

void
PredicateSearch::skipMinFeature(uint32_t doc_id_in)
{
    uint32_t doc_id;
    for (doc_id = _skip->next(); doc_id < doc_id_in; doc_id = _skip->next());

    if (__builtin_expect( ! isAtEnd(doc_id), true)) {
        advanceAllTo(doc_id);
    } else {
        setAtEnd();
    }
}

void
PredicateSearch::doSeek(uint32_t doc_id) {
    skipMinFeature(doc_id);
    while (!_sorted_indexes.empty() && ! isAtEnd()) {
        uint32_t doc_id_0 = _doc_ids[_sorted_indexes[0]];
        uint8_t min_feature = _min_feature_vector[doc_id_0];
        uint8_t k = static_cast<uint8_t>(min_feature == 0 ? 0 : min_feature - 1);
        if (k < _sorted_indexes.size()) {
            uint32_t doc_id_k = _doc_ids[_sorted_indexes[k]];
            if (doc_id_0 == doc_id_k) {
                if (evaluateHit(doc_id_0, k)) {
                    setDocId(doc_id_0);
                    return;
                }
            }
        }
        skipMinFeature(doc_id_0 + 1);
    }
    setAtEnd();
}

void
PredicateSearch::doUnpack(uint32_t doc_id) {
    if (doc_id == getDocId()) {
        if (_termFieldMatchData) {
            auto end = _interval_range_vector[doc_id];
            _termFieldMatchData->setSubqueries(doc_id, _subquery_markers[end]);
        }
    }
}

}
