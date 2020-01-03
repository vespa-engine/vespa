// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/queryeval/searchiterator.h>

namespace search {
namespace queryeval {
namespace test {

/**
 * Child iterator that has initial docid > 0.
 **/
struct EagerChild : public SearchIterator
{
    EagerChild(uint32_t initial) : SearchIterator() { setDocId(initial); }
    void doSeek(uint32_t) override { setAtEnd(); }
    void doUnpack(uint32_t) override {}
};

} // namespace test
} // namespace queryeval
} // namespace search

