// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/vespalib/util/trinary.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <functional>

using search::queryeval::AlwaysTrueBlueprint;
using search::queryeval::Blueprint;
using search::queryeval::EmptyBlueprint;
using search::queryeval::SimpleBlueprint;
using search::queryeval::SimpleResult;
using search::queryeval::SearchIterator;
using vespalib::Trinary;

using Constraint = Blueprint::FilterConstraint;
constexpr auto lower_bound = Constraint::LOWER_BOUND;
constexpr auto upper_bound = Constraint::UPPER_BOUND;

const uint32_t docid_limit = 100;

template <typename T>
concept FilterFactory = requires(const T &a, bool strict, Constraint upper_or_lower) {
    { a.createFilterSearch(strict, upper_or_lower) } -> std::same_as<std::unique_ptr<SearchIterator>>;
};

using factory_fun = std::function<std::unique_ptr<SearchIterator>(const std::vector<Blueprint*> &, bool, Constraint)>;

// Combine children blueprints using a shared filter creation
// algorithm. Satisfies the FilterFactory concept.
struct Combine {
    factory_fun fun;
    std::vector<Blueprint*> list;
    Combine(factory_fun fun_in) : fun(fun_in), list() {}
    Combine &&add(std::unique_ptr<Blueprint> child) && {
        list.push_back(child.release());
        return std::move(*this);
    }
    auto createFilterSearch(bool strict, Constraint upper_or_lower) const {
        return fun(list, strict, upper_or_lower);
    }
    ~Combine() {
        for (auto *ptr: list) {
            delete ptr;
        }
    }
};

// create a leaf blueprint that matches no documents
std::unique_ptr<Blueprint> empty() {
    return std::make_unique<EmptyBlueprint>();
}

// create a leaf blueprint that matches all documents
std::unique_ptr<Blueprint> full() {
    return std::make_unique<AlwaysTrueBlueprint>();
}

// make a simple result containing the given documents
SimpleResult make_result(const std::vector<uint32_t> &docs) {
    SimpleResult result;
    for (uint32_t doc: docs) {
        result.addHit(doc);
    }
    return result;
}

// make a simple result containing all the documents
SimpleResult make_full_result() {
    SimpleResult result;
    for (uint32_t docid = 1; docid < docid_limit; ++docid) {
        result.addHit(docid);
    }
    return result;
}

// make a simple result containing none of the documents
SimpleResult make_empty_result() {
    return SimpleResult();
}

// create a leaf blueprint with the specified hits
std::unique_ptr<Blueprint> leaf(const std::vector<uint32_t> &docs) {
    return std::make_unique<SimpleBlueprint>(make_result(docs));
}

// what kind of results are we expecting from a filter search?
struct Expect {
    Trinary matches_any;
    SimpleResult hits;
    Expect(const std::vector<uint32_t> &hits_in)
      : matches_any(Trinary::Undefined), hits(make_result(hits_in)) {}
    Expect(Trinary matches_any_in) : matches_any(matches_any_in), hits() {
        REQUIRE(matches_any != Trinary::Undefined);
        if (matches_any == Trinary::True) {
            hits = make_full_result();
        } else {
            hits = make_empty_result();
        }
    }
};

template <FilterFactory Blueprint>
void verify(const Blueprint &blueprint, const Expect &upper, const Expect &lower) {
    for (auto constraint: {lower_bound, upper_bound}) {
        const Expect &expect = (constraint == upper_bound) ? upper : lower;
        for (bool strict: {false, true}) {
            auto filter = blueprint.createFilterSearch(strict, constraint);
            EXPECT_EQ(filter->matches_any(), expect.matches_any);
            SimpleResult actual;
            if (strict) {
                actual.searchStrict(*filter, docid_limit);
            } else {
                actual.search(*filter, docid_limit);
            }
            EXPECT_EQ(actual, expect.hits);
        }
    }
}

template <FilterFactory Blueprint>
void verify(const Blueprint &blueprint, const Expect &upper_and_lower) {
    verify(blueprint, upper_and_lower, upper_and_lower);
}

TEST(FilterSearchTest, empty_leaf) {
    verify(*empty(), Expect(Trinary::False));
}

TEST(FilterSearchTest, full_leaf) {
    verify(*full(), Expect(Trinary::True));
}

TEST(FilterSearchTest, custom_leaf) {
    verify(*leaf({5,10,20}), Expect({5,10,20}));
}

TEST(FilterSearchTest, simple_or) {
    verify(Combine(Blueprint::create_or_filter)
           .add(leaf({5, 10}))
           .add(leaf({7}))
           .add(leaf({3, 11})),
           Expect({3, 5, 7, 10, 11}));
}

TEST(FilterSearchTest, simple_and) {
    verify(Combine(Blueprint::create_and_filter)
           .add(leaf({1, 2, 3, 4, 5, 6}))
           .add(leaf({2, 4, 6, 7}))
           .add(leaf({1, 4, 6, 7, 10})),
           Expect({4, 6}));
}

GTEST_MAIN_RUN_ALL_TESTS()
