// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "predicate_posting_list.h"
#include "predicate_interval_store.h"

namespace search::predicate {

/**
 * PredicatePostingList implementation for range query edge iterators (bounds)
 * from PredicateIndex.
 */
template<typename Iterator>
class PredicateBoundsPostingList : public PredicatePostingList {
    const PredicateIntervalStore &_interval_store;
    Iterator _iterator;
    const IntervalWithBounds *_current_interval;
    uint32_t _interval_count;
    uint32_t _value_diff;
    IntervalWithBounds _single_buf;

public:
    PredicateBoundsPostingList(const PredicateIntervalStore &interval_store,Iterator it,uint32_t value_diff);
    bool next(uint32_t doc_id) override;
    bool nextInterval() override;
    VESPA_DLL_LOCAL uint32_t getInterval() const override {
        return _current_interval ? _current_interval->interval : 0;
    }
};

template<typename Iterator>
PredicateBoundsPostingList<Iterator>::PredicateBoundsPostingList(
        const PredicateIntervalStore &interval_store,
        Iterator it, uint32_t value_diff)
        : _interval_store(interval_store),
          _iterator(it),
          _current_interval(0),
          _interval_count(0),
          _value_diff(value_diff) {
}

namespace {
    bool checkBounds(uint32_t bounds, uint32_t diff) {
        if (bounds & 0x80000000) {
            return diff >= (bounds & 0x3fffffff);
        } else if (bounds & 0x40000000) {
            return diff < (bounds & 0x3fffffff);
        } else {
            return (diff >= (bounds >> 16)) && (diff < (bounds & 0xffff));
        }
    }
}  // namespace

template<typename Iterator>
bool
PredicateBoundsPostingList<Iterator>::next(uint32_t doc_id) {
    if (_iterator.valid() && _iterator.getKey() <= doc_id) {
        _iterator.linearSeek(doc_id + 1);
    }
    for (;; ++_iterator) {
        if (!_iterator.valid()) {
            return false;
        }
        _current_interval = _interval_store.get(_iterator.getData(), _interval_count, &_single_buf);
        if (checkBounds(_current_interval->bounds, _value_diff)) {
            break;
        }
        if (nextInterval()) {
            break;
        }
    }
    setDocId(_iterator.getKey());
    return true;
}

template<typename Iterator>
bool
PredicateBoundsPostingList<Iterator>::nextInterval() {
    uint32_t next_bounds;
    do {
        if (__builtin_expect(_interval_count == 1, true)) {
            return false;
        }
        ++_current_interval;
        --_interval_count;
        next_bounds = _current_interval->bounds;
    } while (!checkBounds(next_bounds, _value_diff));
    return true;
}

}
