// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/searchiterator.h>
#include "wand_parts.h"

namespace search {
namespace queryeval {

struct WeakAndSearch : SearchIterator {
    using Terms = wand::Terms;
    virtual size_t get_num_terms() const = 0;
    virtual int32_t get_term_weight(size_t idx) const = 0;
    virtual wand::score_t get_max_score(size_t idx) const = 0;
    virtual const Terms &getTerms() const = 0;
    virtual uint32_t getN() const = 0;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    static SearchIterator::UP createArrayWand(const Terms &terms, uint32_t n, bool strict);
    static SearchIterator::UP createHeapWand(const Terms &terms, uint32_t n, bool strict);
    static SearchIterator::UP create(const Terms &terms, uint32_t n, bool strict);
};

} // namespace queryeval
} // namespace search

