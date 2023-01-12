// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/execution_profiler.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/searchlib/queryeval/profiled_iterator.h>
#include <vespa/searchlib/queryeval/simplesearch.h>
#include <vespa/searchlib/queryeval/andsearch.h>
#include <vespa/searchlib/queryeval/orsearch.h>

#include <memory>

using namespace search::queryeval;
using vespalib::ExecutionProfiler;
using vespalib::Slime;

SearchIterator::UP create_term(const vespalib::string &name, std::vector<uint32_t> hits) {
    auto search = std::make_unique<SimpleSearch>(SimpleResult(hits));
    search->tag(name);
    return search;
}

SearchIterator::UP create_iterator_tree() {
    return AndSearch::create({OrSearch::create({create_term("A", {1,3,5}),
                                                create_term("B", {2,4,6})}, true),
                              OrSearch::create({create_term("C", {4,6,8}),
                                                create_term("D", {5,7,9})}, false)},
        true);
}

TEST(ProfiledIteratorTest, iterator_tree_can_be_profiled) {
    ExecutionProfiler profiler(64);
    auto root = create_iterator_tree();
    root = ProfiledIterator::profile(profiler, std::move(root));
    fprintf(stderr, "%s", root->asString().c_str());
    SimpleResult expect({4,5,6});
    SimpleResult actual;
    actual.searchStrict(*root, 100);
    EXPECT_EQ(actual, expect);
    Slime slime;
    profiler.report(slime.setObject());
    fprintf(stderr, "%s", slime.toString().c_str());
}

GTEST_MAIN_RUN_ALL_TESTS()
