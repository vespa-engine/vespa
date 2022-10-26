// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/isourceselector.h>
#include <vespa/searchlib/queryeval/simple_phrase_blueprint.h>
#include <vespa/searchlib/queryeval/equiv_blueprint.h>
#include <vespa/searchlib/queryeval/weighted_set_term_blueprint.h>
#include <vespa/searchlib/queryeval/dot_product_blueprint.h>
#include <vespa/searchlib/queryeval/same_element_blueprint.h>
#include <vespa/searchlib/queryeval/wand/parallel_weak_and_blueprint.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/vespalib/util/trinary.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <functional>

namespace search::fef { class TermFieldMatchDataArray; }
namespace search::fef { class MatchData; }

using namespace search::queryeval;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldMatchDataArray;
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
    { a.add(std::move(bp)) } -> std::same_as<T&>;
};

template <typename T>
concept ChildCollector = requires(T a, std::unique_ptr<Blueprint> bp) {
    a.addChild(std::move(bp));
};

// inherit Blueprint to capture the default filter factory
struct DefaultBlueprint : Blueprint {
    void optimize(Blueprint* &) override { abort(); }
    const State &getState() const override { abort(); }
    void fetchPostings(const ExecuteInfo &) override { abort(); }
    void freeze() override { abort(); }
    SearchIteratorUP createSearch(MatchData &, bool) const override { abort(); }
};

// add the use of a field to a leaf blueprint (SimplePhraseBlueprint asserts on this)
struct FakeFieldProxy : SimpleLeafBlueprint {
    std::unique_ptr<Blueprint> child;
    FakeFieldProxy(const FieldSpec &field, std::unique_ptr<Blueprint> child_in)
      : SimpleLeafBlueprint(field), child(std::move(child_in))
    {
        setParent(child->getParent());
        child->setParent(this);
    }
    SearchIteratorUP createLeafSearch(const TermFieldMatchDataArray &, bool) const override { abort(); }
    SearchIteratorUP createFilterSearch(bool strict, Constraint upper_or_lower) const override {
        return child->createFilterSearch(strict, upper_or_lower);
    }
};

// need one of these to be able to create a SourceBlender
struct NullSelector : ISourceSelector {
    NullSelector() : ISourceSelector(7) {}
    void setSource(uint32_t, Source) override { abort(); }
    uint32_t getDocIdLimit() const override { abort(); }
    void compactLidSpace(uint32_t) override { abort(); }
    std::unique_ptr<sourceselector::Iterator> createIterator() const override { abort(); }
};

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
std::unique_ptr<Blueprint> hits(const std::vector<uint32_t> &docs) {
    return std::make_unique<SimpleBlueprint>(make_result(docs));
}

// Describes blueprint children with a list of simple factories that
// can later be used to create them.
struct Children {
    using Factory = std::function<Blueprint::UP()>;
    std::vector<Factory> list;
    Children() : list() {}
    size_t size() const { return list.size(); }
    Children &hits(const std::vector<uint32_t> &docs) {
        list.push_back([docs](){ return ::hits(docs); });
        return *this;
    }
    Children &full() {
        list.push_back([](){ return ::full(); });
        return *this;
    }
    Children &empty() {
        list.push_back([](){ return ::empty(); });
        return *this;
    }
    template <FilterFactoryBuilder Builder>
    Builder &apply(Builder &builder) const {
        for (const Factory &make_child: list) {
            builder.add(make_child());
        }
        return builder;
    }
};

// Combine children blueprints using a shared filter creation
// algorithm. Satisfies the FilterFactory concept.
struct Combine {
    using factory_fun = std::function<std::unique_ptr<SearchIterator>(const Blueprint::Children &, bool, Constraint)>;
    factory_fun fun;
    Blueprint::Children list;
    Combine(factory_fun fun_in) noexcept : fun(fun_in), list() {}
    Combine &add(std::unique_ptr<Blueprint> child) {
        list.push_back(std::move(child));
        return *this;
    }
    Combine &add(const Children &children) {
        return children.apply(*this);
    }
    auto createFilterSearch(bool strict, Constraint upper_or_lower) const {
        return fun(list, strict, upper_or_lower);
    }
    ~Combine();
};
Combine::~Combine() = default;

// enable Make-ing source blender
struct SourceBlenderAdapter {
    NullSelector selector;
    SourceBlenderBlueprint blueprint;
    SourceBlenderAdapter() : selector(), blueprint(selector) {}
    void addChild(std::unique_ptr<Blueprint> child) {
        blueprint.addChild(std::move(child));
    }
    auto createFilterSearch(bool strict, Constraint upper_or_lower) const {
        return blueprint.createFilterSearch(strict, upper_or_lower);
    }
};

// enable Make-ing simple phrase
struct SimplePhraseAdapter {
    FieldSpec field;
    SimplePhraseBlueprint blueprint;
    SimplePhraseAdapter() : field("foo", 3, 7), blueprint(field, false) {}
    void addChild(std::unique_ptr<Blueprint> child) {
        auto child_field = blueprint.getNextChildField(field);
        auto term = std::make_unique<FakeFieldProxy>(child_field, std::move(child));
        blueprint.addTerm(std::move(term));
    }
    auto createFilterSearch(bool strict, Constraint upper_or_lower) const {
        return blueprint.createFilterSearch(strict, upper_or_lower);
    }
};

//enable Make-ing equiv
struct EquivAdapter {
    FieldSpecBaseList fields;
    EquivBlueprint blueprint;
    EquivAdapter() : fields(), blueprint(fields, MatchDataLayout()) {}
    void addChild(std::unique_ptr<Blueprint> child) {
        blueprint.addTerm(std::move(child), 1.0);
    }
    auto createFilterSearch(bool strict, Constraint upper_or_lower) const {
        return blueprint.createFilterSearch(strict, upper_or_lower);
    }
};

// enable Make-ing weighted set
struct WeightedSetTermAdapter {
    FieldSpec field;
    WeightedSetTermBlueprint blueprint;
    WeightedSetTermAdapter() : field("foo", 3, 7), blueprint(field) {}
    void addChild(std::unique_ptr<Blueprint> child) {
        blueprint.addTerm(std::move(child), 100);
    }
    auto createFilterSearch(bool strict, Constraint upper_or_lower) const {
        return blueprint.createFilterSearch(strict, upper_or_lower);
    }
};

// enable Make-ing dot product
struct DotProductAdapter {
    FieldSpec field;
    DotProductBlueprint blueprint;
    DotProductAdapter() : field("foo", 3, 7), blueprint(field) {}
    void addChild(std::unique_ptr<Blueprint> child) {
        auto child_field = blueprint.getNextChildField(field);
        auto term = std::make_unique<FakeFieldProxy>(child_field, std::move(child));
        blueprint.addTerm(std::move(term), 100);
    }
    auto createFilterSearch(bool strict, Constraint upper_or_lower) const {
        return blueprint.createFilterSearch(strict, upper_or_lower);
    }
};

// enable Make-ing parallel weak and
struct ParallelWeakAndAdapter {
    FieldSpec field;
    ParallelWeakAndBlueprint blueprint;
    ParallelWeakAndAdapter() : field("foo", 3, 7), blueprint(field, 100, 0.0, 1.0) {}
    void addChild(std::unique_ptr<Blueprint> child) {
        auto child_field = blueprint.getNextChildField(field);
        auto term = std::make_unique<FakeFieldProxy>(child_field, std::move(child));
        blueprint.addTerm(std::move(term), 100);
    }
    auto createFilterSearch(bool strict, Constraint upper_or_lower) const {
        return blueprint.createFilterSearch(strict, upper_or_lower);
    }
};

// enable Make-ing same element
struct SameElementAdapter {
    SameElementBlueprint blueprint;
    SameElementAdapter() : blueprint("foo", false) {}
    void addChild(std::unique_ptr<Blueprint> child) {
        auto child_field = blueprint.getNextChildField("foo", 3);
        auto term = std::make_unique<FakeFieldProxy>(child_field, std::move(child));
        blueprint.addTerm(std::move(term));
    }
    auto createFilterSearch(bool strict, Constraint upper_or_lower) const {
        return blueprint.createFilterSearch(strict, upper_or_lower);
    }
};

// Make a specific intermediate-ish blueprint that you can add
// children to. Satisfies the FilterFactory concept.
template <FilterFactory T>
requires ChildCollector<T>
struct Make {
    T blueprint;
    template <typename ... Args>
    Make(Args && ... args) : blueprint(std::forward<Args>(args)...) {}
    Make &add(std::unique_ptr<Blueprint> child) {
        blueprint.addChild(std::move(child));
        return *this;
    }
    Make &add(const Children &children) {
        return children.apply(*this);
    }
    auto createFilterSearch(bool strict, Constraint upper_or_lower) const {
        return blueprint.createFilterSearch(strict, upper_or_lower);
    }
};

// what kind of results are we expecting from a filter search?
struct Expect {
    Trinary matches_any;
    SimpleResult docs;
    Expect(const std::vector<uint32_t> &docs_in)
      : matches_any(Trinary::Undefined), docs(make_result(docs_in)) {}
    Expect(Trinary matches_any_in) : matches_any(matches_any_in), docs() {
        REQUIRE(matches_any != Trinary::Undefined);
        if (matches_any == Trinary::True) {
            docs = make_full_result();
        } else {
            docs = make_empty_result();
        }
    }
    static Expect empty() { return Expect(Trinary::False); }
    static Expect full() { return Expect(Trinary::True); }
    static Expect hits(const std::vector<uint32_t> &docs) { return Expect(docs); }
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
            EXPECT_EQ(actual, expect.docs);
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
    verify(*hits({5,10,20}), Expect::hits({5,10,20}));
}

TEST(FilterSearchTest, default_blueprint) {
    verify(DefaultBlueprint(), Expect::full(), Expect::empty());
}

TEST(FilterSearchTest, simple_or) {
    auto child_list = Children()
        .hits({5, 10})
        .hits({7})
        .hits({3, 11});
    auto expected = Expect::hits({3, 5, 7, 10, 11});
    verify(Combine(Blueprint::create_or_filter).add(child_list), expected);
    verify(Make<OrBlueprint>().add(child_list), expected);
    verify(Make<EquivAdapter>().add(child_list), expected);
    verify(Make<WeightedSetTermAdapter>().add(child_list), expected);
    verify(Make<DotProductAdapter>().add(child_list), expected);
    verify(Combine(Blueprint::create_atmost_or_filter).add(child_list), expected, Expect::empty());
    verify(Make<WeakAndBlueprint>(100).add(child_list), expected, Expect::empty());
    verify(Make<SourceBlenderAdapter>().add(child_list), expected, Expect::empty());
    verify(Make<ParallelWeakAndAdapter>().add(child_list), expected, Expect::empty());
}

TEST(FilterSearchTest, simple_and) {
    auto child_list = Children()
        .hits({1, 2, 3, 4, 5, 6})
        .hits({2, 4, 6, 7})
        .hits({1, 4, 6, 7, 10});
    auto expected = Expect::hits({4, 6});
    verify(Combine(Blueprint::create_and_filter).add(child_list), expected);
    verify(Make<AndBlueprint>().add(child_list), expected);
    verify(Combine(Blueprint::create_atmost_and_filter).add(child_list), expected, Expect::empty());
    verify(Make<NearBlueprint>(3).add(child_list), expected, Expect::empty());
    verify(Make<ONearBlueprint>(3).add(child_list), expected, Expect::empty());
    verify(Make<SimplePhraseAdapter>().add(child_list), expected, Expect::empty());
    verify(Make<SameElementAdapter>().add(child_list), expected, Expect::empty());
}

TEST(FilterSearchTest, simple_andnot) {
    auto child_list = Children()
        .hits({1, 2, 3, 4, 5, 6})
        .hits({2, 4, 6})
        .hits({4, 6, 7});
    auto expected = Expect::hits({1, 3, 5});
    verify(Combine(Blueprint::create_andnot_filter).add(child_list), expected);
    verify(Make<AndNotBlueprint>().add(child_list), expected);
}

TEST(FilterSearchTest, rank_filter) {
    auto child_list1 = Children().hits({1,2,3}).empty().full();
    auto child_list2 = Children().empty().hits({1,2,3}).full();
    auto child_list3 = Children().full().hits({1,2,3}).empty();
    verify(Combine(Blueprint::create_first_child_filter).add(child_list1), Expect::hits({1,2,3}));
    verify(Combine(Blueprint::create_first_child_filter).add(child_list2), Expect::empty());
    verify(Combine(Blueprint::create_first_child_filter).add(child_list3), Expect::full());
    verify(Make<RankBlueprint>().add(child_list1), Expect::hits({1,2,3}));
    verify(Make<RankBlueprint>().add(child_list2), Expect::empty());
    verify(Make<RankBlueprint>().add(child_list3), Expect::full());
}

GTEST_MAIN_RUN_ALL_TESTS()
