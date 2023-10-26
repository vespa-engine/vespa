// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for PredicateTreeAnalyzer.

#include <vespa/log/log.h>
LOG_SETUP("PredicateTreeAnalyzer_test");

#include <vespa/document/predicate/predicate.h>
#include <vespa/document/predicate/predicate_slime_builder.h>
#include <vespa/searchlib/predicate/predicate_tree_analyzer.h>
#include <vespa/vespalib/testkit/testapp.h>

using document::PredicateSlimeBuilder;
using namespace search;
using namespace search::predicate;
using document::Predicate;
using vespalib::Slime;
using vespalib::slime::Cursor;
using std::map;
using std::string;

namespace {
using Builder = PredicateSlimeBuilder;

TEST("require that minfeature is 1 for simple term") {
    auto slime(Builder().feature("foo").value("bar").build());
    PredicateTreeAnalyzer analyzer(slime->get());
    EXPECT_EQUAL(1, analyzer.getMinFeature());
    EXPECT_EQUAL(1, analyzer.getSize());
    EXPECT_TRUE(analyzer.getSizeMap().empty());
}

TEST("require that minfeature is 1 for simple negative term") {
    auto slime(Builder().neg().feature("foo").value("bar").build());
    PredicateTreeAnalyzer analyzer(slime->get());
    EXPECT_EQUAL(1, analyzer.getMinFeature());
    EXPECT_EQUAL(2, analyzer.getSize());
}

void checkSizeMap(const map<string, int> &map, const string &key, int val) {
    auto it = map.find(key);
    ASSERT_TRUE(it != map.end());
    EXPECT_EQUAL(val, it->second);
}

TEST("require that minfeature is sum for and") {
    auto slime(Builder()
               .and_node({Builder().feature("foo").value("bar"),
                          Builder().feature("baz").value("qux"),
                          Builder().feature("quux").value("corge")}).build());
    PredicateTreeAnalyzer analyzer(slime->get());
    EXPECT_EQUAL(3, analyzer.getMinFeature());
    EXPECT_EQUAL(3, analyzer.getSize());
    EXPECT_EQUAL(3u, analyzer.getSizeMap().size());
    TEST_DO(checkSizeMap(analyzer.getSizeMap(), "a0", 1));
    TEST_DO(checkSizeMap(analyzer.getSizeMap(), "a1", 1));
    TEST_DO(checkSizeMap(analyzer.getSizeMap(), "a2", 1));
}

TEST("require that minfeature is min for or") {
    auto slime(Builder().or_node
               ({Builder().and_node
                       ({Builder().feature("foo").value("bar"),
                         Builder().feature("baz").value("qux"),
                         Builder().feature("quux").value("corge")}),
                 Builder().and_node
                       ({Builder().feature("grault").value("garply"),
                         Builder().feature("waldo").value("fred")})})
               .build());
    PredicateTreeAnalyzer analyzer(slime->get());
    EXPECT_EQUAL(2, analyzer.getMinFeature());
    EXPECT_EQUAL(5, analyzer.getSize());
    EXPECT_EQUAL(5u, analyzer.getSizeMap().size());
    TEST_DO(checkSizeMap(analyzer.getSizeMap(), "o0a0", 1));
    TEST_DO(checkSizeMap(analyzer.getSizeMap(), "o0a1", 1));
    TEST_DO(checkSizeMap(analyzer.getSizeMap(), "o0a2", 1));
    TEST_DO(checkSizeMap(analyzer.getSizeMap(), "o1a0", 1));
    TEST_DO(checkSizeMap(analyzer.getSizeMap(), "o1a1", 1));
}

TEST("require that minfeature rounds up") {
    auto slime(Builder()
               .or_node({Builder().feature("foo").value("bar"),
                         Builder().feature("foo").value("bar"),
                         Builder().feature("foo").value("bar")}).build());
    PredicateTreeAnalyzer analyzer(slime->get());
    EXPECT_EQUAL(1, analyzer.getMinFeature());
    EXPECT_EQUAL(3, analyzer.getSize());
}

TEST("require that multivalue feature set considers all values") {
    {
        auto slime(Builder()
               .and_node({Builder().feature("foo").value("A").value("B"),
                          Builder().feature("foo").value("B")}).build());
        PredicateTreeAnalyzer analyzer(slime->get());
        EXPECT_EQUAL(1, analyzer.getMinFeature());
        EXPECT_EQUAL(2, analyzer.getSize());
    }
    {
        auto slime(Builder()
               .and_node({Builder().feature("foo").value("A").value("B"),
                          Builder().feature("foo").value("C")}).build());
        PredicateTreeAnalyzer analyzer(slime->get());
        EXPECT_EQUAL(2, analyzer.getMinFeature());
        EXPECT_EQUAL(2, analyzer.getSize());
    }
}

TEST("require that not-features don't count towards minfeature calculation") {
    auto slime(Builder()
               .and_node({Builder().feature("foo").value("A"),
                          Builder().neg().feature("foo").value("A"),
                          Builder().neg().feature("foo").value("B"),
                          Builder().feature("foo").value("B")}).build());
    PredicateTreeAnalyzer analyzer(slime->get());
    EXPECT_EQUAL(3, analyzer.getMinFeature());
    EXPECT_EQUAL(6, analyzer.getSize());
}

TEST("require that not-ranges don't count towards minfeature calculation") {
    auto slime(Builder()
               .and_node({Builder().feature("foo").range(0, 10),
                          Builder().neg().feature("foo").range(0, 10),
                          Builder().neg().feature("bar").range(0, 10),
                          Builder().feature("bar").range(0, 10)}).build());
    PredicateTreeAnalyzer analyzer(slime->get());
    EXPECT_EQUAL(3, analyzer.getMinFeature());
    EXPECT_EQUAL(6, analyzer.getSize());
}

TEST("require that multilevel AND stores sizes") {
    auto slime(Builder().and_node
               ({Builder().and_node
                       ({Builder().feature("foo").value("bar"),
                         Builder().feature("baz").value("qux"),
                         Builder().feature("quux").value("corge")}),
                 Builder().and_node
                       ({Builder().feature("grault").value("garply"),
                         Builder().feature("waldo").value("fred")})})
               .build());
    PredicateTreeAnalyzer analyzer(slime->get());
    EXPECT_EQUAL(5, analyzer.getMinFeature());
    EXPECT_EQUAL(5, analyzer.getSize());
    EXPECT_EQUAL(7u, analyzer.getSizeMap().size());
    TEST_DO(checkSizeMap(analyzer.getSizeMap(), "a0", 3));
    TEST_DO(checkSizeMap(analyzer.getSizeMap(), "a1", 2));
    TEST_DO(checkSizeMap(analyzer.getSizeMap(), "a0a0", 1));
    TEST_DO(checkSizeMap(analyzer.getSizeMap(), "a0a1", 1));
    TEST_DO(checkSizeMap(analyzer.getSizeMap(), "a0a2", 1));
    TEST_DO(checkSizeMap(analyzer.getSizeMap(), "a1a0", 1));
    TEST_DO(checkSizeMap(analyzer.getSizeMap(), "a1a1", 1));
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
