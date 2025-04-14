// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/execution_profiler.h>
#include <vespa/vespalib/util/trinary.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/searchlib/queryeval/profiled_iterator.h>
#include <vespa/searchlib/queryeval/wand/weak_and_heap.h>
#include <vespa/searchlib/queryeval/wand/weak_and_search.h>
#include <vespa/searchlib/queryeval/simplesearch.h>
#include <vespa/searchlib/queryeval/sourceblendersearch.h>
#include <vespa/searchlib/queryeval/andsearch.h>
#include <vespa/searchlib/queryeval/orsearch.h>
#include <vespa/searchlib/attribute/fixedsourceselector.h>

#include <memory>
#include <map>

using namespace search::queryeval;
using search::FixedSourceSelector;
using vespalib::ExecutionProfiler;
using vespalib::Slime;
using vespalib::Trinary;

size_t num_docs = 100;

std::string extract_name(std::string_view tag) {
    auto end = tag.find("<");
    auto ns = tag.rfind("::", end);
    size_t begin = (ns > tag.size()) ? 0 : ns + 2;
    return std::string(tag.substr(begin, end - begin));
}

std::string strip_tag(std::string_view tag) {
    std::string prefix;
    std::string suffix;
    size_t begin = 0;
    if (auto pos = tag.find(']'); pos < tag.size()) {
        prefix = tag.substr(0, pos + 1);
        begin = pos + 1;
    }
    auto end = tag.size();
    if (auto pos = tag.rfind("::"); pos < tag.size()) {
        suffix = tag.substr(pos);
        end = pos;
    }
    return prefix + extract_name(tag.substr(begin, end - begin)) + suffix;
}

bool is_true(Trinary a) {
    REQUIRE(a != Trinary::Undefined);
    return (a == Trinary::True);
}

bool is_true(Trinary a, Trinary b) {
    return is_true(a) && is_true(b);
}

struct MySources {
    FixedSourceSelector selector;
    MySources(const std::vector<std::pair<uint32_t,uint32_t>> &entries)
      : selector(123, "<file>", num_docs)
    {
        for (const auto &entry: entries) {
            selector.setSource(entry.first, entry.second);
        }
    }
};
MySources my_sources({{1,3},{3,3},{5,3},
                      {2,5},{4,5},{6,5}});

int id_seq = 0;
void reset_id() { id_seq = 0; }
SearchIterator::UP with_id(SearchIterator::UP search) {
    search->set_id(++id_seq);
    return search;
}

SearchIterator::UP T(std::vector<uint32_t> hits) {
    return with_id(std::make_unique<SimpleSearch>(SimpleResult(hits), true));
}

SearchIterator::UP t(std::vector<uint32_t> hits) {
    return with_id(std::make_unique<SimpleSearch>(SimpleResult(hits), false));
}

SearchIterator::UP OR(SearchIterator::UP s1, SearchIterator::UP s2) {
    bool strict = is_true(s1->is_strict(), s2->is_strict());
    return with_id(OrSearch::create({std::move(s1), std::move(s2)}, strict));
}

SearchIterator::UP AND(SearchIterator::UP s1, SearchIterator::UP s2) {
    bool strict = is_true(s1->is_strict());
    return with_id(AndSearch::create({std::move(s1), std::move(s2)}, strict));
}

SearchIterator::UP blend(SearchIterator::UP s1, uint32_t id1,
                         SearchIterator::UP s2, uint32_t id2)
{
    bool strict = is_true(s1->is_strict(), s2->is_strict());
    SourceBlenderSearch::Children list;
    list.emplace_back(s1.release(), id1);
    list.emplace_back(s2.release(), id2);
    return with_id(SourceBlenderSearch::create(my_sources.selector.createIterator(), list, strict));
}

SearchIterator::UP create_term(std::vector<uint32_t> hits) {
    reset_id();
    return T(hits);
}

SearchIterator::UP create_iterator_tree() {
    reset_id();
    return AND(OR(T({4,6,8}),
                  T({5,7,9})),
               blend(t({1,3,5,7,9}), 3,
                     t({2,4,6,8}), 5));
}

SearchIterator::UP create_weak_and() {
    reset_id();
    struct DummyHeap : WeakAndHeap {
        void adjust(score_t *, score_t *) override {}
        DummyHeap() : WeakAndHeap(100) {}
    };
    static DummyHeap dummy_heap;
    WeakAndSearch::Terms terms;
    terms.emplace_back(T({1,2,3}).release(), 100, 3);
    terms.emplace_back(T({5,6}).release(), 200, 2);
    terms.emplace_back(T({8}).release(), 300, 1);
    return with_id(WeakAndSearch::create(terms, wand::MatchParams(dummy_heap), wand::Bm25TermFrequencyScorer(num_docs), 100, true, true));
}

void collect(std::map<std::string,size_t> &counts, const auto &node) {
    if (!node.valid()) {
        return;
    }
    collect(counts, node["roots"]);
    collect(counts, node["children"]);
    for (size_t i = 0; i < node.entries(); ++i) {
        collect(counts, node[i]);
    }
    const auto &name = node["name"];
    if (name.valid()) {
        auto name_str = strip_tag(name.asString().make_stringview());
        counts[name_str] += node["count"].asLong();
    }
};

std::map<std::string,size_t> collect_counts(const auto &root) {
    std::map<std::string,size_t> counts;
    collect(counts, root);
    return counts;
}

void print_counts(const std::map<std::string,size_t> &counts) {
    for (const auto &[name, count]: counts) {
        fprintf(stderr, "%s: %zu\n", name.c_str(), count);
    }
}

void verify_result(SearchIterator &search, const std::vector<uint32_t> &hits) {
    SimpleResult actual;
    SimpleResult expect(hits);
    actual.searchStrict(search, num_docs);
    EXPECT_EQ(actual, expect);
}

void verify_termwise_result(SearchIterator &search, const std::vector<uint32_t> &hits) {
    search.initRange(1, num_docs);
    auto result = search.get_hits(1);
    ASSERT_EQ(result->size(), num_docs);
    uint32_t pos = 1;
    for (uint32_t hit: hits) {
        while (pos < hit) {
            EXPECT_FALSE(result->testBit(pos++));
        }
        EXPECT_TRUE(result->testBit(pos++));
    }
}

void verify_operation(ExecutionProfiler &profiler, std::map<std::string,size_t> &seen, const std::string &expect) {
    Slime slime;
    profiler.report(slime.setObject());
    auto counts = collect_counts(slime.get());
    for (const auto &[name, count]: counts) {
        if (name == expect) {
            EXPECT_EQ(count, ++seen[name]);
        } else {
            EXPECT_EQ(count, seen[name]);
        }
    }
}

TEST(ProfiledIteratorTest, init_seek_unpack_termwise_is_profiled) {
    ExecutionProfiler profiler(64);
    std::map<std::string,size_t> seen;
    auto root = ProfiledIterator::profile(profiler, create_term({1,2,3}));
    root->initRange(1,4);
    verify_operation(profiler, seen, "[1]SimpleSearch::initRange");
    root->seek(2);
    verify_operation(profiler, seen, "[1]SimpleSearch::doSeek");
    root->unpack(2);
    verify_operation(profiler, seen, "[1]SimpleSearch::doUnpack");
    root->initRange(1,4);
    verify_operation(profiler, seen, "[1]SimpleSearch::initRange");
    auto bits = root->get_hits(1);
    verify_operation(profiler, seen, "[1]SimpleSearch::get_hits");
    root->initRange(1,4);
    verify_operation(profiler, seen, "[1]SimpleSearch::initRange");
    root->or_hits_into(*bits, 1);
    verify_operation(profiler, seen, "[1]SimpleSearch::or_hits_into");
    root->initRange(1,4);
    verify_operation(profiler, seen, "[1]SimpleSearch::initRange");
    root->and_hits_into(*bits, 1);
    verify_operation(profiler, seen, "[1]SimpleSearch::and_hits_into");
}

TEST(ProfiledIteratorTest, iterator_tree_can_be_profiled) {
    ExecutionProfiler profiler(64);
    auto root = create_iterator_tree();
    root = ProfiledIterator::profile(profiler, std::move(root));
    fprintf(stderr, "%s", root->asString().c_str());
    verify_termwise_result(*root, {4,5,6});
    verify_result(*root, {4,5,6});
    Slime slime;
    profiler.report(slime.setObject());
    fprintf(stderr, "%s", slime.toString().c_str());
    auto counts = collect_counts(slime.get());
    print_counts(counts);
    EXPECT_EQ(counts["[7]AndSearchStrict::initRange"], 2);
    EXPECT_EQ(counts["[3]StrictHeapOrSearch::initRange"], 2);
    EXPECT_EQ(counts["[1]SimpleSearch::initRange"], 2);
    EXPECT_EQ(counts["[2]SimpleSearch::initRange"], 2);
    EXPECT_EQ(counts["[6]SourceBlenderSearchNonStrict::initRange"], 2);
    EXPECT_EQ(counts["[4]SimpleSearch::initRange"], 2);
    EXPECT_EQ(counts["[5]SimpleSearch::initRange"], 2);
}

TEST(ProfiledIteratorTest, weak_and_can_be_profiled) {
    ExecutionProfiler profiler(64);
    auto root = create_weak_and();
    root = ProfiledIterator::profile(profiler, std::move(root));
    fprintf(stderr, "%s", root->asString().c_str());
    verify_result(*root, {1,2,3,5,6,8});
    Slime slime;
    profiler.report(slime.setObject());
    fprintf(stderr, "%s", slime.toString().c_str());
    auto counts = collect_counts(slime.get());
    print_counts(counts);
    EXPECT_EQ(counts["[4]WeakAndSearchLR::initRange"], 1);
    EXPECT_EQ(counts["[1]SimpleSearch::initRange"], 1);
    EXPECT_EQ(counts["[2]SimpleSearch::initRange"], 1);
    EXPECT_EQ(counts["[3]SimpleSearch::initRange"], 1);
    EXPECT_EQ(counts["[4]WeakAndSearchLR::doSeek"], 7);
    EXPECT_EQ(counts["[1]SimpleSearch::doSeek"], 4);
    EXPECT_EQ(counts["[2]SimpleSearch::doSeek"], 3);
    EXPECT_EQ(counts["[3]SimpleSearch::doSeek"], 2);
}

GTEST_MAIN_RUN_ALL_TESTS()
