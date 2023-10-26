// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_printer.

#include <vespa/log/log.h>
LOG_SETUP("predicate_printer_test");

#include <vespa/document/predicate/predicate.h>
#include <vespa/document/predicate/predicate_printer.h>
#include <vespa/document/predicate/predicate_slime_builder.h>
#include <vespa/vespalib/testkit/testapp.h>

using vespalib::Slime;
using vespalib::slime::Cursor;
using namespace document;

namespace {

using SlimeUP = std::unique_ptr<Slime>;
using namespace document::predicate_slime_builder;

TEST("require that PredicatePrinter prints FeatureSets") {
    PredicateSlimeBuilder builder;
    builder.feature("foo").value("bar").value("baz");
    ASSERT_EQUAL("'foo' in ['bar','baz']",
                 PredicatePrinter::print(*builder.build()));

    builder.feature("foo").value("bar");
    ASSERT_EQUAL("'foo' in ['bar']",
                 PredicatePrinter::print(*builder.build()));
}

TEST("require that PredicatePrinter escapes non-ascii characters") {
    PredicateSlimeBuilder builder;
    builder.feature("\n\t\001'").value("\xc3\xb8");
    ASSERT_EQUAL("'\\n\\t\\x01\\x27' in ['\\xc3\\xb8']",
                 PredicatePrinter::print(*builder.build()));
}

TEST("require that PredicatePrinter prints FeatureRanges") {
    PredicateSlimeBuilder builder;
    builder.feature("foo").range(-10, 42);
    ASSERT_EQUAL("'foo' in [-10..42]",
                 PredicatePrinter::print(*builder.build()));
}

TEST("require that PredicatePrinter prints open ended FeatureRanges") {
    PredicateSlimeBuilder builder;
    builder.feature("foo").greaterEqual(-10);
    ASSERT_EQUAL("'foo' in [-10..]",
                 PredicatePrinter::print(*builder.build()));

    builder.feature("foo").lessEqual(42);
    ASSERT_EQUAL("'foo' in [..42]", PredicatePrinter::print(*builder.build()));
}

TEST("require that PredicatePrinter prints NOT_IN FeatureSets") {
    PredicateSlimeBuilder builder;
    builder.neg().feature("foo").value("bar").value("baz");
    ASSERT_EQUAL("'foo' not in ['bar','baz']",
                 PredicatePrinter::print(*builder.build()));
}

TEST("require that PredicatePrinter can negate FeatureRanges") {
    auto slime = neg(featureRange("foo", -10, 42));
    ASSERT_EQUAL("'foo' not in [-10..42]", PredicatePrinter::print(*slime));
}

TEST("require that PredicatePrinter can negate open ended FeatureRanges") {
    auto slime = neg(greaterEqual("foo", 42));
    ASSERT_EQUAL("'foo' not in [42..]", PredicatePrinter::print(*slime));

    slime = neg(lessEqual("foo", 42));
    ASSERT_EQUAL("'foo' not in [..42]", PredicatePrinter::print(*slime));
}

TEST("require that PredicatePrinter can negate double open ended ranges") {
    auto slime = neg(emptyRange("foo"));
    ASSERT_EQUAL("'foo' not in [..]", PredicatePrinter::print(*slime));
}

TEST("require that PredicatePrinter prints AND expressions") {
    auto slime = andNode({featureSet("foo", {"bar", "baz"}),
                          featureSet("foo", {"bar", "baz"})});
    ASSERT_EQUAL("('foo' in ['bar','baz'] and 'foo' in ['bar','baz'])",
                 PredicatePrinter::print(*slime));
}

TEST("require that PredicatePrinter prints OR expressions") {
    auto slime = orNode({featureSet("foo", {"bar", "baz"}),
                         featureSet("foo", {"bar", "baz"})});
    ASSERT_EQUAL("('foo' in ['bar','baz'] or 'foo' in ['bar','baz'])",
                 PredicatePrinter::print(*slime));
}

TEST("require that PredicatePrinter can negate OR expressions") {
    auto slime = neg(orNode({featureSet("foo", {"bar", "baz"}),
                             featureSet("foo", {"bar", "baz"})}));
    ASSERT_EQUAL("not ('foo' in ['bar','baz'] or 'foo' in ['bar','baz'])",
                 PredicatePrinter::print(*slime));
}

TEST("require that PredicatePrinter can negate AND expressions") {
    auto slime = neg(andNode({featureSet("foo", {"bar", "baz"}),
                              featureSet("foo", {"bar", "baz"})}));
    ASSERT_EQUAL("not ('foo' in ['bar','baz'] and 'foo' in ['bar','baz'])",
                 PredicatePrinter::print(*slime));
}

TEST("require that PredicatePrinter prints True") {
    auto slime = truePredicate();
    ASSERT_EQUAL("true", PredicatePrinter::print(*slime));
}

TEST("require that PredicatePrinter prints False") {
    auto slime = falsePredicate();
    ASSERT_EQUAL("false", PredicatePrinter::print(*slime));
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
