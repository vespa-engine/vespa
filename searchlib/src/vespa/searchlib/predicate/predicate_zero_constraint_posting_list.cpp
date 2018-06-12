// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.predicate.predicate_zero_constraint_posting_list");

#include "predicate_zero_constraint_posting_list.h"

namespace search {
namespace predicate {

PredicateZeroConstraintPostingList::PredicateZeroConstraintPostingList(Iterator it)
    : _iterator(it) {}

bool PredicateZeroConstraintPostingList::next(uint32_t doc_id) {
    if (_iterator.valid() && _iterator.getKey() <= doc_id) {
        _iterator.linearSeek(doc_id + 1);
    }
    if (!_iterator.valid()) {
        return false;
    }
    setDocId(_iterator.getKey());
    return true;
}

}  // namespace search::predicate
}  // namespace search
