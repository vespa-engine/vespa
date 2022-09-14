// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "global_filter.h"
#include "blueprint.h"
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/util/thread_bundle.h>
#include <vespa/searchlib/common/bitvector.h>
#include <cassert>

using vespalib::Runnable;
using vespalib::ThreadBundle;

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

std::unique_ptr<BitVector> make_part(Blueprint &blueprint, uint32_t begin, uint32_t end) {
    bool strict = true;
    auto constraint = Blueprint::FilterConstraint::UPPER_BOUND;
    auto filter_iterator = blueprint.createFilterSearch(strict, constraint);
    filter_iterator->initRange(begin, end);
    auto result = filter_iterator->get_hits(begin);
    // count bits in parallel and cache the results for later
    result->countTrueBits();
    return result;
}

struct MakePart : Runnable {
    Blueprint &blueprint;
    uint32_t begin;
    uint32_t end;
    std::unique_ptr<BitVector> result;
    MakePart(Blueprint &blueprint_in, uint32_t begin_in, uint32_t end_in) noexcept
      : blueprint(blueprint_in), begin(begin_in), end(end_in), result() {}
    void run() override { result = make_part(blueprint, begin, end); }
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
    uint32_t total_size = 1;
    uint32_t total_count = 0;
    std::vector<uint32_t> splits;
    splits.reserve(vectors.size());
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

std::shared_ptr<GlobalFilter>
GlobalFilter::create(Blueprint &blueprint, uint32_t docid_limit, ThreadBundle &thread_bundle)
{
    uint32_t num_threads = thread_bundle.size();
    std::vector<MakePart> parts;
    parts.reserve(num_threads);
    uint32_t docid = 1;
    uint32_t per_thread = (docid_limit - docid) / num_threads;
    uint32_t rest_docs = (docid_limit - docid) % num_threads;
    while (docid < docid_limit) {
        uint32_t part_size = per_thread + (parts.size() < rest_docs);
        parts.emplace_back(blueprint, docid, docid + part_size);
        docid += part_size;
    }
    assert(parts.size() <= num_threads);
    assert((docid == docid_limit) || parts.empty());
    thread_bundle.run(parts);
    if (parts.size() == 1) {
        return create(std::move(parts[0].result));
    }
    std::vector<std::unique_ptr<BitVector>> vectors;
    vectors.reserve(parts.size());
    for (MakePart &part: parts) {
        vectors.push_back(std::move(part.result));
    }
    return create(std::move(vectors));
}

}
