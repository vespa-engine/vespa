// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "global_filter.h"
#include <vespa/vespalib/util/require.h>

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

struct MultiBitVectorFilter : public GlobalFilter {
    std::vector<std::unique_ptr<BitVector>> vectors;
    std::vector<uint32_t> splits;
    uint32_t total_size;
    uint32_t total_count;
    MultiBitVectorFilter(std::vector<std::unique_ptr<BitVector>> vectors_in,
                         std::vector<uint32_t> splits_in,
                         uint32_t total_size_in,
                         uint32_t total_count_in)
      : vectors(std::move(vectors_in)),
        splits(std::move(splits_in)),
        total_size(total_size_in),
        total_count(total_count_in) {}
    bool is_active() const override { return true; }
    uint32_t size() const override { return total_size; }
    uint32_t count() const override { return total_count; }
    bool check(uint32_t docid) const override {
        size_t i = 0;
        while ((i < splits.size()) && (docid >= splits[i])) {
            ++i;
        }
        return vectors[i]->testBit(docid);
    }
};

}

GlobalFilter::GlobalFilter() = default;
GlobalFilter::~GlobalFilter() = default;

std::shared_ptr<GlobalFilter>
GlobalFilter::create() {
    return std::make_shared<Inactive>();
}

std::shared_ptr<GlobalFilter>
GlobalFilter::create(std::vector<uint32_t> docids, uint32_t size)
{
    uint32_t prev = 0;
    auto bits = BitVector::create(1, size);
    for (uint32_t docid: docids) {
        REQUIRE(docid > prev);
        REQUIRE(docid < size);
        bits->setBit(docid);
        prev = docid;
    }
    bits->invalidateCachedCount();
    return create(std::move(bits));
}

std::shared_ptr<GlobalFilter>
GlobalFilter::create(std::unique_ptr<BitVector> vector)
{
    return std::make_shared<BitVectorFilter>(std::move(vector));
}

std::shared_ptr<GlobalFilter>
GlobalFilter::create(std::vector<std::unique_ptr<BitVector>> vectors)
{
    uint32_t total_size = 0;
    uint32_t total_count = 0;
    std::vector<uint32_t> splits;
    for (size_t i = 0; i < vectors.size(); ++i) {
        bool last = ((i + 1) == vectors.size());
        total_count += vectors[i]->countTrueBits();
        if (last) {
            total_size = vectors[i]->size();
        } else {
            REQUIRE_EQ(vectors[i]->size(), vectors[i + 1]->getStartIndex());
            splits.push_back(vectors[i]->size());
        }
    }
    return std::make_shared<MultiBitVectorFilter>(std::move(vectors), std::move(splits),
                                                  total_size, total_count);
}

}
