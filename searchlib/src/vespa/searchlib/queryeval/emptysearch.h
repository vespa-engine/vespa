// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"

namespace search {
namespace queryeval {

class EmptySearch : public SearchIterator
{
protected:
    void doSeek(uint32_t) override;
    void doUnpack(uint32_t) override;
    void initRange(uint32_t begin, uint32_t end) override {
        SearchIterator::initRange(begin, end);
        setAtEnd();
    }

public:
    EmptySearch();
    ~EmptySearch();
};

} // namespace queryeval
} // namespace search

