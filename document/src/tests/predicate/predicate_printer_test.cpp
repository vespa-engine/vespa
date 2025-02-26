// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_printer.

#include <vespa/document/predicate/predicate.h>
#include <vespa/document/predicate/predicate_printer.h>
#include <vespa/document/predicate/predicate_slime_builder.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("predicate_printer_test");

using vespalib::Slime;
using vespalib::slime::Cursor;
using namespace document;

namespace {

using SlimeUP = std::unique_ptr<Slime>;
using namespace document::predicate_slime_builder;

TEST(PredicatePrinterTest, require_that_PredicatePrinter_prints_FeatureSets)
{
    PredicateSlimeBuilder builder;
    builder.feature("foo").value("bar").value("baz");
    ASSERT_EQ("'foo' in ['bar','baz']",
                 PredicatePrinter::print(*builder.build()));

    builder.feature("foo").value("bar");
    ASSERT_EQ("'foo' in ['bar']",
                 PredicatePrinter::print(*builder.build()));
}

TEST(PredicatePrinterTest, require_that_PredicatePrinter_escapes_non_ascii_characters)
{
    PredicateSlimeBuilder builder;
    builder.feature("\n\t\001'").value("\xc3\xb8");
    ASSERT_EQ("'\\n\\t\\x01\\x27' in ['\\xc3\\xb8']",
                 PredicatePrinter::print(*builder.build()));
}

TEST(PredicatePrinterTest, require_that_PredicatePrinter_prints_FeatureRanges)
{
    PredicateSlimeBuilder builder;
    builder.feature("foo").range(-10, 42);
    ASSERT_EQ("'foo' in [-10..42]",
                 PredicatePrinter::print(*builder.build()));
}

TEST(PredicatePrinterTest, require_that_PredicatePrinter_prints_open_ended_FeatureRanges)
{
    PredicateSlimeBuilder builder;
    builder.feature("foo").greaterEqual(-10);
    ASSERT_EQ("'foo' in [-10..]",
                 PredicatePrinter::print(*builder.build()));

    builder.feature("foo").lessEqual(42);
    ASSERT_EQ("'foo' in [..42]", PredicatePrinter::print(*builder.build()));
}

TEST(PredicatePrinterTest, require_that_PredicatePrinter_prints_NOT_IN_FeatureSets)
{
    PredicateSlimeBuilder builder;
    builder.neg().feature("foo").value("bar").value("baz");
    ASSERT_EQ("'foo' not in ['bar','baz']",
                 PredicatePrinter::print(*builder.build()));
}

TEST(PredicatePrinterTest, require_that_PredicatePrinter_can_negate_FeatureRanges)
{
    auto slime = neg(featureRange("foo", -10, 42));
    ASSERT_EQ("'foo' not in [-10..42]", PredicatePrinter::print(*slime));
}

TEST(PredicatePrinterTest, require_that_PredicatePrinter_can_negate_open_ended_FeatureRanges)
{
    auto slime = neg(greaterEqual("foo", 42));
    ASSERT_EQ("'foo' not in [42..]", PredicatePrinter::print(*slime));

    slime = neg(lessEqual("foo", 42));
    ASSERT_EQ("'foo' not in [..42]", PredicatePrinter::print(*slime));
}

TEST(PredicatePrinterTest, require_that_PredicatePrinter_can_negate_double_open_ended_ranges)
{
    auto slime = neg(emptyRange("foo"));
    ASSERT_EQ("'foo' not in [..]", PredicatePrinter::print(*slime));
}

TEST(PredicatePrinterTest, require_that_PredicatePrinter_prints_AND_expressions)
{
    auto slime = andNode({featureSet("foo", {"bar", "baz"}),
                          featureSet("foo", {"bar", "baz"})});
    ASSERT_EQ("('foo' in ['bar','baz'] and 'foo' in ['bar','baz'])",
                 PredicatePrinter::print(*slime));
}

TEST(PredicatePrinterTest, require_that_PredicatePrinter_prints_OR_expressions)
{
    auto slime = orNode({featureSet("foo", {"bar", "baz"}),
                         featureSet("foo", {"bar", "baz"})});
    ASSERT_EQ("('foo' in ['bar','baz'] or 'foo' in ['bar','baz'])",
                 PredicatePrinter::print(*slime));
}

TEST(PredicatePrinterTest, require_that_PredicatePrinter_can_negate_OR_expressions)
{
    auto slime = neg(orNode({featureSet("foo", {"bar", "baz"}),
                             featureSet("foo", {"bar", "baz"})}));
    ASSERT_EQ("not ('foo' in ['bar','baz'] or 'foo' in ['bar','baz'])",
                 PredicatePrinter::print(*slime));
}

TEST(PredicatePrinterTest, require_that_PredicatePrinter_can_negate_AND_expressions)
{
    auto slime = neg(andNode({featureSet("foo", {"bar", "baz"}),
                              featureSet("foo", {"bar", "baz"})}));
    ASSERT_EQ("not ('foo' in ['bar','baz'] and 'foo' in ['bar','baz'])",
                 PredicatePrinter::print(*slime));
}

TEST(PredicatePrinterTest, require_that_PredicatePrinter_prints_True)
{
    auto slime = truePredicate();
    ASSERT_EQ("true", PredicatePrinter::print(*slime));
}

TEST(PredicatePrinterTest, require_that_PredicatePrinter_prints_False)
{
    auto slime = falsePredicate();
    ASSERT_EQ("false", PredicatePrinter::print(*slime));
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
