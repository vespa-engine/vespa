// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "predicate_posting_list.h"
#include "common.h"

namespace search::predicate {

/**
 * PredicatePostingList implementation for zero constraint documents
 * from PredicateIndex.
 */
class PredicateZeroConstraintPostingList : public PredicatePostingList {
    using Iterator = ZeroConstraintDocs::Iterator;
    Iterator _iterator;

public:
    PredicateZeroConstraintPostingList(Iterator it);
    bool next(uint32_t doc_id) override;
    bool nextInterval() override { return false; }
    VESPA_DLL_LOCAL uint32_t getInterval() const override { return 0x00010001; }
};

}
