// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/isourceselector.h>
#include <vespa/vespalib/util/trinary.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <functional>

namespace search::fef { class MatchData; }

using namespace search::queryeval;
using vespalib::Trinary;

using Constraint = Blueprint::FilterConstraint;
constexpr auto lower_bound = Constraint::LOWER_BOUND;
constexpr auto upper_bound = Constraint::UPPER_BOUND;

const uint32_t docid_limit = 100;

template <typename T>
concept FilterFactory = requires(const T &a, bool strict, Constraint upper_or_lower) {
    { a.createFilterSearch(strict, upper_or_lower) } -> std::same_as<std::unique_ptr<SearchIterator>>;
};

template <typename T>
concept FilterFactoryBuilder = requires(T a, std::unique_ptr<Blueprint> bp) {
    { std::move(a).add(std::move(bp)) } -> std::same_as<T&&>;
};

// inherit Blueprint to capture the default filter factory
struct DefaultBlueprint : Blueprint {
    void optimize(Blueprint* &) override { abort(); }
    const State &getState() const override { abort(); }
    void fetchPostings(const ExecuteInfo &) override { abort(); }
    void freeze() override { abort(); }
    SearchIteratorUP createSearch(search::fef::MatchData &, bool) const override { abort(); }
};

// need one of these to be able to create a SourceBlender
struct NullSelector : ISourceSelector {
    NullSelector() : ISourceSelector(7) {}
    void setSource(uint32_t, Source) override { abort(); }
    uint32_t getDocIdLimit() const override { abort(); }
    void compactLidSpace(uint32_t) override { abort(); }
    std::unique_ptr<sourceselector::Iterator> createIterator() const override { abort(); }
};
NullSelector null_selector;

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

// create a leaf blueprint that matches no documents
std::unique_ptr<Blueprint> empty() {
    return std::make_unique<EmptyBlueprint>();
}

// create a leaf blueprint that matches all documents
std::unique_ptr<Blueprint> full() {
    return std::make_unique<AlwaysTrueBlueprint>();
}

// create a leaf blueprint with the specified hits
std::unique_ptr<Blueprint> leaf(const std::vector<uint32_t> &docs) {
    return std::make_unique<SimpleBlueprint>(make_result(docs));
}

// Describes blueprint children with a list of simple factories that
// can later be used to create them.
struct Children {
    using Factory = std::function<Blueprint::UP()>;
    std::vector<Factory> list;
    Children() : list() {}
    size_t size() const { return list.size(); }
    Children &&leaf(const std::vector<uint32_t> &docs) && {
        list.push_back([docs](){ return ::leaf(docs); });
        return std::move(*this);
    }
    Children &&full() && {
        list.push_back([](){ return ::full(); });
        return std::move(*this);
    }
    Children &&empty() && {
        list.push_back([](){ return ::empty(); });
        return std::move(*this);
    }
    template <FilterFactoryBuilder Builder>
    Builder &&apply(Builder &&builder) const {
        for (const Factory &make_child: list) {
            std::move(builder).add(make_child());
        }
        return std::move(builder);
    }
};

// Combine children blueprints using a shared filter creation
// algorithm. Satisfies the FilterFactory concept.
struct Combine {
    using factory_fun = std::function<std::unique_ptr<SearchIterator>(const Blueprint::Children &, bool, Constraint)>;
    factory_fun fun;
    Blueprint::Children list;
    Combine(factory_fun fun_in) noexcept : fun(fun_in), list() {}
    Combine &&add(std::unique_ptr<Blueprint> child) && {
        list.push_back(std::move(child));
        return std::move(*this);
    }
    Combine &&add(const Children &children) && {
        return children.apply(std::move(*this));
    }
    auto createFilterSearch(bool strict, Constraint upper_or_lower) const {
        return fun(list, strict, upper_or_lower);
    }
    ~Combine();
};
Combine::~Combine() = default;

// Make a specific (intermediate) blueprint that you can add children
// to. Satisfies the FilterFactory concept.
template <FilterFactory T>
struct Make {
    T blueprint;
    template <typename ... Args>
    Make(Args && ... args) : blueprint(std::forward<Args>(args)...) {}
    Make &&add(std::unique_ptr<Blueprint> child) && {
        blueprint.addChild(std::move(child));
        return std::move(*this);
    }
    Make &&add(const Children &children) && {
        return children.apply(std::move(*this));
    }
    auto createFilterSearch(bool strict, Constraint upper_or_lower) const {
        return blueprint.createFilterSearch(strict, upper_or_lower);
    }
};

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
    static Expect empty() { return Expect(Trinary::False); }
    static Expect full() { return Expect(Trinary::True); }
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
    verify(*empty(), Expect::empty());
}

TEST(FilterSearchTest, full_leaf) {
    verify(*full(), Expect::full());
}

TEST(FilterSearchTest, custom_leaf) {
    verify(*leaf({5,10,20}), Expect({5,10,20}));
}

TEST(FilterSearchTest, default_blueprint) {
    verify(DefaultBlueprint(), Expect::full(), Expect::empty());
}

TEST(FilterSearchTest, simple_or) {
    auto child_list = Children()
        .leaf({5, 10})
        .leaf({7})
        .leaf({3, 11});
    auto expected = Expect({3, 5, 7, 10, 11});
    verify(Combine(Blueprint::create_or_filter).add(child_list), expected);
    verify(Make<OrBlueprint>().add(child_list), expected);
    verify(Combine(Blueprint::create_atmost_or_filter).add(child_list), expected, Expect::empty());
    verify(Make<WeakAndBlueprint>(child_list.size()).add(child_list), expected, Expect::empty());
    verify(Make<SourceBlenderBlueprint>(null_selector).add(child_list), expected, Expect::empty());
}

TEST(FilterSearchTest, simple_and) {
    auto child_list = Children()
        .leaf({1, 2, 3, 4, 5, 6})
        .leaf({2, 4, 6, 7})
        .leaf({1, 4, 6, 7, 10});
    auto expected = Expect({4, 6});
    verify(Combine(Blueprint::create_and_filter).add(child_list), expected);
    verify(Make<AndBlueprint>().add(child_list), expected);
    verify(Combine(Blueprint::create_atmost_and_filter).add(child_list), expected, Expect::empty());
    verify(Make<NearBlueprint>(3).add(child_list), expected, Expect::empty());
    verify(Make<ONearBlueprint>(3).add(child_list), expected, Expect::empty());
}

TEST(FilterSearchTest, simple_andnot) {
    auto child_list = Children()
        .leaf({1, 2, 3, 4, 5, 6})
        .leaf({2, 4, 6})
        .leaf({4, 6, 7});
    auto expected = Expect({1, 3, 5});
    verify(Combine(Blueprint::create_andnot_filter).add(child_list), expected);
    verify(Make<AndNotBlueprint>().add(child_list), expected);
}

TEST(FilterSearchTest, rank_filter) {
    auto child_list1 = Children().leaf({1,2,3}).empty().full();
    auto child_list2 = Children().empty().leaf({1,2,3}).full();
    auto child_list3 = Children().full().leaf({1,2,3}).empty();
    verify(Combine(Blueprint::create_first_child_filter).add(child_list1), Expect({1,2,3}));
    verify(Combine(Blueprint::create_first_child_filter).add(child_list2), Expect::empty());
    verify(Combine(Blueprint::create_first_child_filter).add(child_list3), Expect::full());
    verify(Make<RankBlueprint>().add(child_list1), Expect({1,2,3}));
    verify(Make<RankBlueprint>().add(child_list2), Expect::empty());
    verify(Make<RankBlueprint>().add(child_list3), Expect::full());
}

GTEST_MAIN_RUN_ALL_TESTS()
