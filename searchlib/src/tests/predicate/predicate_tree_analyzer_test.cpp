// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for PredicateTreeAnalyzer.

#include <vespa/document/predicate/predicate.h>
#include <vespa/document/predicate/predicate_slime_builder.h>
#include <vespa/searchlib/predicate/predicate_tree_analyzer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <iomanip>
#include <sstream>

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

TEST(PredicateTreAnalyzerTest, require_that_minfeature_is_1_for_simple_term) {
    auto slime(Builder().feature("foo").value("bar").build());
    PredicateTreeAnalyzer analyzer(slime->get());
    EXPECT_EQ(1, analyzer.getMinFeature());
    EXPECT_EQ(1, analyzer.getSize());
    EXPECT_TRUE(analyzer.getSizeMap().empty());
}

TEST(PredicateTreAnalyzerTest, require_that_minfeature_is_1_for_simple_negative_term) {
    auto slime(Builder().neg().feature("foo").value("bar").build());
    PredicateTreeAnalyzer analyzer(slime->get());
    EXPECT_EQ(1, analyzer.getMinFeature());
    EXPECT_EQ(2, analyzer.getSize());
}

void checkSizeMap(const map<string, int> &map, const string &key, int val) {
    std::ostringstream os;
    os << "key=" << std::quoted(key) << ", val=" << val;
    SCOPED_TRACE(os.str());
    auto it = map.find(key);
    ASSERT_TRUE(it != map.end());
    EXPECT_EQ(val, it->second);
}

TEST(PredicateTreAnalyzerTest, require_that_minfeature_is_sum_for_and) {
    auto slime(Builder()
               .and_node({Builder().feature("foo").value("bar"),
                          Builder().feature("baz").value("qux"),
                          Builder().feature("quux").value("corge")}).build());
    PredicateTreeAnalyzer analyzer(slime->get());
    EXPECT_EQ(3, analyzer.getMinFeature());
    EXPECT_EQ(3, analyzer.getSize());
    EXPECT_EQ(3u, analyzer.getSizeMap().size());
    checkSizeMap(analyzer.getSizeMap(), "a0", 1);
    checkSizeMap(analyzer.getSizeMap(), "a1", 1);
    checkSizeMap(analyzer.getSizeMap(), "a2", 1);
}

TEST(PredicateTreAnalyzerTest, require_that_minfeature_is_min_for_or) {
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
    EXPECT_EQ(2, analyzer.getMinFeature());
    EXPECT_EQ(5, analyzer.getSize());
    EXPECT_EQ(5u, analyzer.getSizeMap().size());
    checkSizeMap(analyzer.getSizeMap(), "o0a0", 1);
    checkSizeMap(analyzer.getSizeMap(), "o0a1", 1);
    checkSizeMap(analyzer.getSizeMap(), "o0a2", 1);
    checkSizeMap(analyzer.getSizeMap(), "o1a0", 1);
    checkSizeMap(analyzer.getSizeMap(), "o1a1", 1);
}

TEST(PredicateTreAnalyzerTest, require_that_minfeature_rounds_up) {
    auto slime(Builder()
               .or_node({Builder().feature("foo").value("bar"),
                         Builder().feature("foo").value("bar"),
                         Builder().feature("foo").value("bar")}).build());
    PredicateTreeAnalyzer analyzer(slime->get());
    EXPECT_EQ(1, analyzer.getMinFeature());
    EXPECT_EQ(3, analyzer.getSize());
}

TEST(PredicateTreAnalyzerTest, require_that_multivalue_feature_set_considers_all_values) {
    {
        auto slime(Builder()
               .and_node({Builder().feature("foo").value("A").value("B"),
                          Builder().feature("foo").value("B")}).build());
        PredicateTreeAnalyzer analyzer(slime->get());
        EXPECT_EQ(1, analyzer.getMinFeature());
        EXPECT_EQ(2, analyzer.getSize());
    }
    {
        auto slime(Builder()
               .and_node({Builder().feature("foo").value("A").value("B"),
                          Builder().feature("foo").value("C")}).build());
        PredicateTreeAnalyzer analyzer(slime->get());
        EXPECT_EQ(2, analyzer.getMinFeature());
        EXPECT_EQ(2, analyzer.getSize());
    }
}

TEST(PredicateTreAnalyzerTest, require_that_not_features_dont_count_towards_minfeature_calculation) {
    auto slime(Builder()
               .and_node({Builder().feature("foo").value("A"),
                          Builder().neg().feature("foo").value("A"),
                          Builder().neg().feature("foo").value("B"),
                          Builder().feature("foo").value("B")}).build());
    PredicateTreeAnalyzer analyzer(slime->get());
    EXPECT_EQ(3, analyzer.getMinFeature());
    EXPECT_EQ(6, analyzer.getSize());
}

TEST(PredicateTreAnalyzerTest, require_that_not_ranges_dont_count_towards_minfeature_calculation) {
    auto slime(Builder()
               .and_node({Builder().feature("foo").range(0, 10),
                          Builder().neg().feature("foo").range(0, 10),
                          Builder().neg().feature("bar").range(0, 10),
                          Builder().feature("bar").range(0, 10)}).build());
    PredicateTreeAnalyzer analyzer(slime->get());
    EXPECT_EQ(3, analyzer.getMinFeature());
    EXPECT_EQ(6, analyzer.getSize());
}

TEST(PredicateTreAnalyzerTest, require_that_multilevel_AND_stores_sizes) {
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
    EXPECT_EQ(5, analyzer.getMinFeature());
    EXPECT_EQ(5, analyzer.getSize());
    EXPECT_EQ(7u, analyzer.getSizeMap().size());
    checkSizeMap(analyzer.getSizeMap(), "a0", 3);
    checkSizeMap(analyzer.getSizeMap(), "a1", 2);
    checkSizeMap(analyzer.getSizeMap(), "a0a0", 1);
    checkSizeMap(analyzer.getSizeMap(), "a0a1", 1);
    checkSizeMap(analyzer.getSizeMap(), "a0a2", 1);
    checkSizeMap(analyzer.getSizeMap(), "a1a0", 1);
    checkSizeMap(analyzer.getSizeMap(), "a1a1", 1);
}

}  // namespace
