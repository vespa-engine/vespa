// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "global_filter.h"

namespace search::queryeval {

namespace {

struct Inactive : GlobalFilter {
    bool is_active() const override { return false; }
    uint32_t size() const override { abort(); }
    uint32_t count() const override { abort(); }
    bool check(uint32_t) const override { abort(); }
};

struct BitVectorFilter : public GlobalFilter {
    std::unique_ptr<BitVector> vector;
    BitVectorFilter(std::unique_ptr<BitVector> vector_in)
      : vector(std::move(vector_in)) {}
    bool is_active() const override { return true; }
    uint32_t size() const override { return vector->size(); }
    uint32_t count() const override { return vector->countTrueBits(); }
    bool check(uint32_t docid) const override { return vector->testBit(docid); }
};

}

GlobalFilter::GlobalFilter() = default;
GlobalFilter::~GlobalFilter() = default;

std::shared_ptr<GlobalFilter>
GlobalFilter::create() {
    return std::make_shared<Inactive>();
}

std::shared_ptr<GlobalFilter>
GlobalFilter::create(std::unique_ptr<BitVector> vector) {
    return std::make_shared<BitVectorFilter>(std::move(vector));
}

}
