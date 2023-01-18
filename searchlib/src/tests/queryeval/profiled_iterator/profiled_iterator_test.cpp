// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/execution_profiler.h>
#include <vespa/vespalib/util/trinary.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/searchlib/queryeval/profiled_iterator.h>
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

SearchIterator::UP t(std::vector<uint32_t> hits) {
    auto search = std::make_unique<SimpleSearch>(SimpleResult(hits), false);
    search->tag("t");
    return search;
}

SearchIterator::UP T(std::vector<uint32_t> hits) {
    auto search = std::make_unique<SimpleSearch>(SimpleResult(hits), true);
    search->tag("T");
    return search;
}

SearchIterator::UP OR(SearchIterator::UP s1, SearchIterator::UP s2) {
    bool strict = is_true(s1->is_strict(), s2->is_strict());
    return OrSearch::create({std::move(s1), std::move(s2)}, strict);
}

SearchIterator::UP AND(SearchIterator::UP s1, SearchIterator::UP s2) {
    bool strict = is_true(s1->is_strict());
    return AndSearch::create({std::move(s1), std::move(s2)}, strict);
}

SearchIterator::UP blend(SearchIterator::UP s1, uint32_t id1,
                         SearchIterator::UP s2, uint32_t id2)
{
    bool strict = is_true(s1->is_strict(), s2->is_strict());
    SourceBlenderSearch::Children list;
    list.emplace_back(s1.release(), id1);
    list.emplace_back(s2.release(), id2);
    return SourceBlenderSearch::create(my_sources.selector.createIterator(), list, strict);
}

SearchIterator::UP create_iterator_tree() {
    return AND(OR(T({4,6,8}),
                  T({5,7,9})),
               blend(t({1,3,5,7,9}), 3,
                     t({2,4,6,8}), 5));
}

void collect(std::map<vespalib::string,size_t> &counts, const auto &node) {
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
        auto name_str = name.asString().make_string();
        counts[name_str] += node["count"].asLong();
    }
};

std::map<vespalib::string,size_t> collect_counts(const auto &root) {
    std::map<vespalib::string,size_t> counts;
    collect(counts, root);
    return counts;
}

void print_counts(const std::map<vespalib::string,size_t> &counts) {
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

void verify_operation(ExecutionProfiler &profiler, std::map<vespalib::string,size_t> &seen, const vespalib::string &expect) {
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
    std::map<vespalib::string,size_t> seen;
    auto root = ProfiledIterator::profile(profiler, T({1,2,3}));
    root->initRange(1,4);
    verify_operation(profiler, seen, "/SimpleSearch/init");
    root->seek(2);
    verify_operation(profiler, seen, "/SimpleSearch/seek");
    root->unpack(2);
    verify_operation(profiler, seen, "/SimpleSearch/unpack");
    root->initRange(1,4);
    verify_operation(profiler, seen, "/SimpleSearch/init");
    auto bits = root->get_hits(1);
    verify_operation(profiler, seen, "/SimpleSearch/termwise");
    root->initRange(1,4);
    verify_operation(profiler, seen, "/SimpleSearch/init");
    root->or_hits_into(*bits, 1);
    verify_operation(profiler, seen, "/SimpleSearch/termwise");
    root->initRange(1,4);
    verify_operation(profiler, seen, "/SimpleSearch/init");
    root->and_hits_into(*bits, 1);
    verify_operation(profiler, seen, "/SimpleSearch/termwise");
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
    EXPECT_EQ(counts["/AndSearchStrict/init"], 2);
    EXPECT_EQ(counts["/0/OrLikeSearch/init"], 2);
    EXPECT_EQ(counts["/0/0/SimpleSearch/init"], 2);
    EXPECT_EQ(counts["/0/1/SimpleSearch/init"], 2);
    EXPECT_EQ(counts["/1/SourceBlenderSearchNonStrict/init"], 2);
    EXPECT_EQ(counts["/1/0/SimpleSearch/init"], 2);
    EXPECT_EQ(counts["/1/1/SimpleSearch/init"], 2);
}

GTEST_MAIN_RUN_ALL_TESTS()
