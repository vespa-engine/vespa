// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "predicate_posting_list.h"
#include "predicate_interval_store.h"
#include "predicate_interval.h"

namespace search::predicate {

/**
 * PredicatePostingList implementation for zstar iterators from
 * PredicateIndex.
 */
template <typename Iterator>
class PredicateZstarCompressedPostingList : public PredicatePostingList {
    const PredicateIntervalStore &_interval_store;
    Iterator _iterator;
    const Interval *_current_interval;
    uint32_t _interval_count;
    uint32_t _interval;
    uint32_t _prev_interval;

    void setInterval(uint32_t interval) { _interval = interval; }
public:
    PredicateZstarCompressedPostingList(const PredicateIntervalStore &store, Iterator it);
    bool next(uint32_t doc_id) override;
    bool nextInterval() override;
    VESPA_DLL_LOCAL uint32_t getInterval() const override { return _interval; }
};

template <typename Iterator>
PredicateZstarCompressedPostingList<Iterator>::PredicateZstarCompressedPostingList(
        const PredicateIntervalStore &interval_store, Iterator it)
        : _interval_store(interval_store),
          _iterator(it),
          _current_interval(0),
          _interval_count(0),
          _interval(0),
          _prev_interval(0) {
}

template<typename Iterator>
bool
PredicateZstarCompressedPostingList<Iterator>::next(uint32_t doc_id) {
    if (_iterator.valid() && _iterator.getKey() <= doc_id) {
        _iterator.linearSeek(doc_id + 1);
    }
    if (!_iterator.valid()) {
        return false;
    }
    Interval single_buf;
    _current_interval = _interval_store.get(_iterator.getData(), _interval_count, &single_buf);
    setDocId(_iterator.getKey());
    setInterval(_current_interval[0].interval);
    _prev_interval = getInterval();
    return true;
}

template<typename Iterator>
bool
PredicateZstarCompressedPostingList<Iterator>::nextInterval() {
    uint32_t next_interval = UINT32_MAX;
    if (_interval_count > 1) {
        next_interval = _current_interval[1].interval;
    }
    if (_prev_interval) {
        if ((next_interval & 0xffff0000) == 0) {
            setInterval(_prev_interval >> 16 | next_interval << 16);
            ++_current_interval;
            --_interval_count;
        } else {
            uint32_t value = _prev_interval >> 16;
            setInterval((value + 1) << 16 | value);
        }
        _prev_interval = 0;
        return true;
    } else if (next_interval != UINT32_MAX) {
        ++_current_interval;
        --_interval_count;
        setInterval(next_interval);
        _prev_interval = next_interval;
        return true;
    }
    return false;
}

}
