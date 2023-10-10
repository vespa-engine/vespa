// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "predicate_posting_list.h"
#include "predicate_interval_store.h"

namespace search::predicate {

/**
 * PredicatePostingList implementation for regular interval iterators
 * from PredicateIndex.
 */
template<typename Iterator>
class PredicateIntervalPostingList : public PredicatePostingList {
    const PredicateIntervalStore &_interval_store;
    Iterator                      _iterator;
    const Interval               *_current_interval;
    uint32_t                      _interval_count;
    Interval                      _single_buf;

public:
    PredicateIntervalPostingList(const PredicateIntervalStore &interval_store, Iterator it);
    bool next(uint32_t doc_id) override;
    VESPA_DLL_LOCAL bool nextInterval() override {
        if (_interval_count == 1) {
            return false;
        } else {
            ++_current_interval;
            --_interval_count;
            return true;
        }
    }
    VESPA_DLL_LOCAL uint32_t getInterval() const override {
        return _current_interval ? _current_interval->interval : 0;
    }
};

template<typename Iterator>
PredicateIntervalPostingList<Iterator>::PredicateIntervalPostingList(
        const PredicateIntervalStore &interval_store, Iterator it)
        : _interval_store(interval_store),
          _iterator(it),
          _current_interval(nullptr),
          _interval_count(0) {
}

template<typename Iterator>
bool
PredicateIntervalPostingList<Iterator>::next(uint32_t doc_id) {
    if (!_iterator.valid()) {
        return false;
    }
    if (__builtin_expect(_iterator.getKey() <= doc_id, true)) {
        _iterator.linearSeek(doc_id + 1);
        if (!_iterator.valid()) {
            return false;
        }
    }
    _current_interval = _interval_store.get(_iterator.getData(), _interval_count, &_single_buf);
    setDocId(_iterator.getKey());
    return true;
}

}
