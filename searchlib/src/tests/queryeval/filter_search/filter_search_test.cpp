// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/i_element_gap_inspector.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/queryeval/full_search.h>
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
#include <vespa/searchlib/queryeval/test/mock_element_gap_inspector.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/vespalib/util/trinary.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <functional>
#include <typeindex>

namespace search::fef { class TermFieldMatchDataArray; }
namespace search::fef { class MatchData; }

using namespace search::queryeval;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchDataArray;
using search::queryeval::test::MockElementGapInspector;
using vespalib::Trinary;

using Constraint = Blueprint::FilterConstraint;
constexpr auto lower_bound = Constraint::LOWER_BOUND;
constexpr auto upper_bound = Constraint::UPPER_BOUND;

const uint32_t docid_limit = 100;

template <typename T>
concept FilterFactory = requires(const T &a, T &ma, InFlow in_flow, uint32_t my_docid_limit, bool strict, Constraint constraint) {
    ma.basic_plan(in_flow, my_docid_limit);
    { a.createFilterSearch(constraint) } -> std::same_as<std::unique_ptr<SearchIterator>>;
};

template <typename T>
concept ChildCollector = requires(T a, std::unique_ptr<Blueprint> bp) {
    a.addChild(std::move(bp));
};

// proxy class used to make various decorators for leaf blueprints
// may be used directly to add the use of a field to a leaf blueprint
struct LeafProxy : SimpleLeafBlueprint {
    std::unique_ptr<Blueprint> child;
    void init() {
        setParent(child->getParent());
        child->setParent(this);
    }
    LeafProxy(std::unique_ptr<Blueprint> child_in)
      : SimpleLeafBlueprint(), child(std::move(child_in)) { init(); }
    LeafProxy(FieldSpecBase field, std::unique_ptr<Blueprint> child_in)
      : SimpleLeafBlueprint(field), child(std::move(child_in)) { init(); }
    ~LeafProxy() override;
    void each_node_post_order(const std::function<void(Blueprint&)> &f) override {
        child->each_node_post_order(f);
        f(*this);
    }
    FlowStats calculate_flow_stats(uint32_t my_docid_limit) const override {
        return child->calculate_flow_stats(my_docid_limit);
    }
    void sort(InFlow in_flow) override {
        resolve_strict(in_flow);
        child->sort(in_flow);
    }
    SearchIteratorUP createLeafSearch(const TermFieldMatchDataArray &) const override { abort(); }
    SearchIteratorUP createFilterSearchImpl(Constraint constraint) const override {
        return child->createFilterSearch(constraint);
    }
};

LeafProxy::~LeafProxy() = default;

// check strictness and filter constraints when creating a filter search
struct CheckParamsProxy : LeafProxy {
    static bool current_strict;           // <- changed by test
    static Constraint current_constraint; // <- changed by test
    bool expect_forced_strict = false;
    bool expect_inherit_strict;
    bool expect_same_constraint;
    CheckParamsProxy(std::unique_ptr<Blueprint> child_in, bool expect_inherit_strict_in, bool expect_same_constraint_in)
      : LeafProxy(std::move(child_in)),
        expect_inherit_strict(expect_inherit_strict_in), expect_same_constraint(expect_same_constraint_in) {}
    CheckParamsProxy(std::unique_ptr<Blueprint> child_in)
      : LeafProxy(std::move(child_in)), expect_forced_strict(true), expect_inherit_strict(false), expect_same_constraint(true) {}
    ~CheckParamsProxy() override;
    SearchIteratorUP createFilterSearchImpl(Constraint constraint) const override {
        if (expect_forced_strict) {
            EXPECT_EQ(strict(), true);
        } else {
            EXPECT_EQ(strict(), (current_strict && expect_inherit_strict));
        }
        EXPECT_EQ((constraint == current_constraint), expect_same_constraint);
        return child->createFilterSearch(constraint);
    }
};

CheckParamsProxy::~CheckParamsProxy() = default;

bool CheckParamsProxy::current_strict = false;
Constraint CheckParamsProxy::current_constraint = lower_bound;

// check dropped blueprints (due to short-circuit)
struct CheckDroppedProxy : LeafProxy {
    mutable bool used;
    CheckDroppedProxy(std::unique_ptr<Blueprint> child_in)
      : LeafProxy(std::move(child_in)), used(false) {}
    ~CheckDroppedProxy() override;
    SearchIteratorUP createFilterSearchImpl(Constraint constraint) const override {
        used = true;
        return child->createFilterSearch(constraint);
    }
};

CheckDroppedProxy::~CheckDroppedProxy()
{
    EXPECT_EQ(used, false);
}

// need one of these to be able to create a SourceBlender
struct NullSelector : ISourceSelector {
    NullSelector() : ISourceSelector(7) {}
    void setSource(uint32_t, Source) override { abort(); }
    uint32_t getDocIdLimit() const override { abort(); }
    void compactLidSpace(uint32_t) override { abort(); }
    std::unique_ptr<sourceselector::Iterator> createIterator() const override { abort(); }
};

MockElementGapInspector mock_element_gap_inspector(std::nullopt);

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

// check filter creation parameters
std::unique_ptr<Blueprint> check(std::unique_ptr<Blueprint> child, bool expect_inherit_strict, bool expect_same_constraint) {
    return std::make_unique<CheckParamsProxy>(std::move(child), expect_inherit_strict, expect_same_constraint);
}

std::unique_ptr<Blueprint> check_forced(std::unique_ptr<Blueprint> child) {
    return std::make_unique<CheckParamsProxy>(std::move(child));
}

// check that create filter is not called
std::unique_ptr<Blueprint> dropped(std::unique_ptr<Blueprint> child) {
    return std::make_unique<CheckDroppedProxy>(std::move(child));
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
    Children &check(bool expect_inherit_strict, bool expect_same_constraint) {
        Factory old_factory = list.back();
        list.back() = [=](){ return ::check(old_factory(), expect_inherit_strict, expect_same_constraint); };
        return *this;
    }
    Children &check_forced() {
        Factory old_factory = list.back();
        list.back() = [=](){ return ::check_forced(old_factory()); };
        return *this;
    }
    Children &dropped() {
        Factory old_factory = list.back();
        list.back() = [=](){ return ::dropped(old_factory()); };
        return *this;
    }
    template <ChildCollector Builder>
    void apply(Builder &builder) const {
        for (const Factory &make_child: list) {
            builder.addChild(make_child());
        }
    }
};

struct NoFlow {
    NoFlow(InFlow) noexcept {}
    void add(double) noexcept {};
    bool strict() noexcept { return false; }
    double flow() noexcept { return 0.0; }
};

// Combine children blueprints using a shared filter creation
// algorithm. Satisfies the FilterFactory concept.
template <typename Flow>
struct Combine {
    using factory_fun = std::function<std::unique_ptr<SearchIterator>(const Blueprint::Children &, Constraint)>;
    factory_fun fun;
    Blueprint::Children list;
    Combine(factory_fun fun_in, const Children &child_list)
      : fun(fun_in), list()
    {
        child_list.apply(*this);
    }
    ~Combine();
    void addChild(std::unique_ptr<Blueprint> child) {
        list.push_back(std::move(child));
    }
    void basic_plan(InFlow in_flow, uint32_t my_docid_limit) {
        Flow flow(in_flow);
        for (auto &child: list) {
            child->basic_plan(InFlow(flow.strict(), flow.flow()), my_docid_limit);
            flow.add(child->estimate());
        }
    }
    auto createFilterSearch(Constraint constraint) const {
        return fun(list, constraint);
    }
};
template <typename Flow>
Combine<Flow>::~Combine() = default;

// enable Make-ing source blender
struct SourceBlenderAdapter {
    NullSelector selector;
    SourceBlenderBlueprint blueprint;
    SourceBlenderAdapter() : selector(), blueprint(selector) {}
    void addChild(std::unique_ptr<Blueprint> child) {
        blueprint.addChild(std::move(child));
    }
    void basic_plan(InFlow in_flow, uint32_t my_docid_limit) {
        blueprint.basic_plan(in_flow, my_docid_limit);
    }
    auto createFilterSearch(Constraint constraint) const {
        return blueprint.createFilterSearch(constraint);
    }
};

// enable Make-ing simple phrase
struct SimplePhraseAdapter {
    FieldSpec field;
    SimplePhraseBlueprint blueprint;
    MatchDataLayout fake_layout;
    SimplePhraseAdapter() : field("foo", 3, 7), blueprint(field, false) {}
    void addChild(std::unique_ptr<Blueprint> child) {
        auto child_field = SimplePhraseBlueprint::next_child_field(field, fake_layout);
        auto term = std::make_unique<LeafProxy>(child_field, std::move(child));
        blueprint.addTerm(std::move(term));
    }
    void basic_plan(InFlow in_flow, uint32_t my_docid_limit) {
        blueprint.basic_plan(in_flow, my_docid_limit);
    }
    auto createFilterSearch(Constraint constraint) const {
        return blueprint.createFilterSearch(constraint);
    }
    ~SimplePhraseAdapter();
};

SimplePhraseAdapter::~SimplePhraseAdapter() = default;

//enable Make-ing equiv
struct EquivAdapter {
    FieldSpecBaseList fields;
    EquivBlueprint blueprint;
    EquivAdapter() : fields(), blueprint(fields, MatchDataLayout()) {}
    void addChild(std::unique_ptr<Blueprint> child) {
        blueprint.addTerm(std::move(child), 1.0);
    }
    void basic_plan(InFlow in_flow, uint32_t my_docid_limit) {
        blueprint.basic_plan(in_flow, my_docid_limit);
    }
    auto createFilterSearch(Constraint constraint) const {
        return blueprint.createFilterSearch(constraint);
    }
};

// enable Make-ing weighted set
struct WeightedSetTermAdapter {
    FieldSpec field;
    WeightedSetTermBlueprint blueprint;
    WeightedSetTermAdapter();
    ~WeightedSetTermAdapter();
    void addChild(std::unique_ptr<Blueprint> child) {
        Blueprint::HitEstimate estimate = blueprint.getState().estimate();
        blueprint.addTerm(std::move(child), 100, estimate);
        blueprint.complete(estimate);
    }
    void basic_plan(InFlow in_flow, uint32_t my_docid_limit) {
        blueprint.basic_plan(in_flow, my_docid_limit);
    }
    auto createFilterSearch(Constraint constraint) const {
        return blueprint.createFilterSearch(constraint);
    }
};

WeightedSetTermAdapter::WeightedSetTermAdapter() : field("foo", 3, 7), blueprint(field) {}
WeightedSetTermAdapter::~WeightedSetTermAdapter() = default;

// enable Make-ing dot product
struct DotProductAdapter {
    FieldSpec field;
    DotProductBlueprint blueprint;
    DotProductAdapter();
    ~DotProductAdapter();
    void addChild(std::unique_ptr<Blueprint> child) {
        auto child_field = blueprint.getNextChildField(field);
        auto term = std::make_unique<LeafProxy>(child_field, std::move(child));
        Blueprint::HitEstimate estimate = blueprint.getState().estimate();
        blueprint.addTerm(std::move(term), 100, estimate);
        blueprint.complete(estimate);
    }
    void basic_plan(InFlow in_flow, uint32_t my_docid_limit) {
        blueprint.basic_plan(in_flow, my_docid_limit);
    }
    auto createFilterSearch(Constraint constraint) const {
        return blueprint.createFilterSearch(constraint);
    }
};

DotProductAdapter::DotProductAdapter() : field("foo", 3, 7), blueprint(field) {}
DotProductAdapter::~DotProductAdapter() = default;

// enable Make-ing parallel weak and
struct ParallelWeakAndAdapter {
    FieldSpec field;
    ParallelWeakAndBlueprint blueprint;
    ParallelWeakAndAdapter() : field("foo", 3, 7), blueprint(field, 100, 0.0, 1.0, true) {}
    void addChild(std::unique_ptr<Blueprint> child) {
        auto child_field = blueprint.getNextChildField(field);
        auto term = std::make_unique<LeafProxy>(child_field, std::move(child));
        Blueprint::HitEstimate estimate = blueprint.getState().estimate();
        blueprint.addTerm(std::move(term), 100, estimate);
        blueprint.complete(estimate);
    }
    void basic_plan(InFlow in_flow, uint32_t my_docid_limit) {
        blueprint.basic_plan(in_flow, my_docid_limit);
    }
    auto createFilterSearch(Constraint constraint) const {
        return blueprint.createFilterSearch(constraint);
    }
};

// enable Make-ing same element
struct SameElementAdapter {
    FieldSpec field;
    mutable std::vector<std::unique_ptr<Blueprint>> children;
    mutable std::unique_ptr<SameElementBlueprint> blueprint;
    SameElementAdapter();
    ~SameElementAdapter();
    void addChild(std::unique_ptr<Blueprint> child) {
        assert(!blueprint);
        auto term = std::make_unique<LeafProxy>(std::move(child));

        children.emplace_back(std::move(term));
    }
    void make_blueprint() const {
        if (!blueprint) {
            std::vector<TermFieldHandle> descendants_index_handles;
            blueprint = std::make_unique<SameElementBlueprint>(field, descendants_index_handles, false);
            for (auto& child : children) {
                blueprint->addChild(std::move(child));
            }
        }
    }
    void basic_plan(InFlow in_flow, uint32_t my_docid_limit) {
        make_blueprint();
        blueprint->basic_plan(in_flow, my_docid_limit);
    }
    auto createFilterSearch(Constraint constraint) const {
        make_blueprint();
        return blueprint->createFilterSearch(constraint);
    }
};

SameElementAdapter::SameElementAdapter()
    : field("foo", 5, 11),
      children(),
      blueprint()
{
}

SameElementAdapter::~SameElementAdapter() = default;

// Make a specific intermediate-ish blueprint that you can add
// children to. Satisfies the FilterFactory concept.
template <FilterFactory T>
requires ChildCollector<T>
struct Make {
    T blueprint;
    template <typename ... Args>
    Make(const Children &child_list, Args && ... args) : blueprint(std::forward<Args>(args)...) {
        child_list.apply(blueprint);
    }
    void basic_plan(InFlow in_flow, uint32_t my_docid_limit) {
        blueprint.basic_plan(in_flow, my_docid_limit);
    }
    auto createFilterSearch(Constraint constraint) const {
        return blueprint.createFilterSearch(constraint);
    }
};

// what kind of results are we expecting from a filter search?
struct Expect {
    Trinary matches_any = Trinary::Undefined;
    SimpleResult docs;
    size_t children = 0;
    Expect(const std::vector<uint32_t> &docs_in) : docs(make_result(docs_in)) {}
    Expect(Trinary matches_any_in) : matches_any(matches_any_in) {
        REQUIRE(matches_any != Trinary::Undefined);
        if (matches_any == Trinary::True) {
            docs = make_full_result();
        } else {
            docs = make_empty_result();
        }
    }
    Expect &child_count(size_t n) { children = n;  return *this; }
    static Expect empty() { return Expect(Trinary::False); }
    static Expect full() { return Expect(Trinary::True); }
    static Expect hits(const std::vector<uint32_t> &docs) { return Expect(docs); }
};

template <FilterFactory Blueprint>
void verify(Blueprint &&blueprint, bool strict, Constraint constraint, const Expect &expect) {
    CheckParamsProxy::current_strict = strict;
    CheckParamsProxy::current_constraint = constraint;
    blueprint.basic_plan(strict, docid_limit);
    auto filter = blueprint.createFilterSearch(constraint);
    if (expect.children >  0) {
        ASSERT_EQ(filter->isMultiSearch(), true);
        EXPECT_EQ(static_cast<MultiSearch*>(filter.get())->getChildren().size(), expect.children);
    }
    EXPECT_EQ(filter->matches_any(), expect.matches_any);
    switch (filter->matches_any()) {
    case Trinary::True:
        EXPECT_EQ(std::type_index(typeid(*filter)), std::type_index(typeid(FullSearch)));
        break;
    case Trinary::False:
        EXPECT_EQ(std::type_index(typeid(*filter)), std::type_index(typeid(EmptySearch)));
    default:
        break;
    }
    SimpleResult actual;
    actual.search(*filter, docid_limit);
    EXPECT_EQ(actual, expect.docs);
}

template <FilterFactory Blueprint>
void verify(Blueprint &&blueprint, bool strict, const Expect &expect) {
    for (auto constraint: {lower_bound, upper_bound}) {
        verify(blueprint, strict, constraint, expect);
    }
}

template <FilterFactory Blueprint>
void verify(Blueprint &&blueprint, const Expect &upper, const Expect &lower) {
    for (auto constraint: {lower_bound, upper_bound}) {
        const Expect &expect = (constraint == upper_bound) ? upper : lower;
        for (bool strict: {false, true}) {
            verify(blueprint, strict, constraint, expect);
        }
    }
}

template <FilterFactory Blueprint>
void verify(Blueprint &&blueprint, const Expect &upper_and_lower) {
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

TEST(FilterSearchTest, default_filter) {
    auto adapter = [](const auto &ignore_children, Constraint constraint) {
                       (void) ignore_children;
                       return Blueprint::create_default_filter(constraint);
                   };
    verify(Combine<NoFlow>(adapter, Children()), Expect::full(), Expect::empty());
}

TEST(FilterSearchTest, simple_or) {
    auto child_list = Children()
        .hits({5, 10}).check(true, true)
        .hits({7}).check(true, true)
        .hits({3, 11}).check(true, true);
    auto expected = Expect::hits({3, 5, 7, 10, 11});
    verify(Combine<OrFlow>(Blueprint::create_or_filter, child_list), expected);
    verify(Make<OrBlueprint>(child_list), expected);
    verify(Make<EquivAdapter>(child_list), expected);
    verify(Combine<OrFlow>(Blueprint::create_atmost_or_filter, child_list), expected, Expect::empty());
    verify(Make<WeakAndBlueprint>(child_list, 100), expected, Expect::empty());
    verify(Make<SourceBlenderAdapter>(child_list), expected, Expect::empty());
    verify(Make<ParallelWeakAndAdapter>(child_list), expected, Expect::empty());
}

TEST(FilterSearchTest, forced_or) {
    auto child_list = Children()
        .hits({5, 10}).check_forced()
        .hits({7}).check_forced()
        .hits({3, 11}).check_forced();
    auto expected = Expect::hits({3, 5, 7, 10, 11});
    verify(Make<WeightedSetTermAdapter>(child_list), expected);
    verify(Make<DotProductAdapter>(child_list), expected);
}

TEST(FilterSearchTest, simple_and) {
    auto child_list = Children()
        .hits({2, 4, 6, 7}).check(true, true)
        .hits({1, 4, 6, 7, 10}).check(false, true)
        .hits({1, 2, 3, 4, 5, 6}).check(false, true);
    auto expected = Expect::hits({4, 6});
    verify(Combine<AndFlow>(Blueprint::create_and_filter, child_list), expected);
    verify(Make<AndBlueprint>(child_list), expected);
    verify(Combine<AndFlow>(Blueprint::create_atmost_and_filter, child_list), expected, Expect::empty());
    verify(Make<NearBlueprint>(child_list, 3, 0, 0, mock_element_gap_inspector), expected, Expect::empty());
    verify(Make<ONearBlueprint>(child_list, 3, 0, 0, mock_element_gap_inspector), expected, Expect::empty());
    verify(Make<SameElementAdapter>(child_list), expected, Expect::empty());
}

TEST(FilterSearchTest, partial_and) {
    auto child_list = Children()
        .hits({2, 4, 6, 7}).check(true, true)
        .hits({1, 4, 6, 7, 10}).check(false, true)
        .hits({1, 2, 3, 4, 5, 6}).check(false, true);
    auto expected = Expect::hits({4, 6, 7});
    verify(Make<NearBlueprint>(child_list, 3, 1, 0, mock_element_gap_inspector), expected, Expect::empty());
    verify(Make<ONearBlueprint>(child_list, 3, 1, 0, mock_element_gap_inspector), expected, Expect::empty());
}

TEST(FilterSearchTest, eager_and) {
    auto child_list = Children()
        .hits({2, 4, 6, 7}).check(true, true)
        .hits({1, 4, 6, 7, 10}).check(true, true)
        .hits({1, 2, 3, 4, 5, 6}).check(true, true);
    auto expected = Expect::hits({4, 6});
    verify(Make<SimplePhraseAdapter>(child_list), expected, Expect::empty());
}

TEST(FilterSearchTest, simple_andnot) {
    auto child_list = Children()
        .hits({1, 2, 3, 4, 5, 6}).check(true, true)
        .hits({2, 4, 6}).check(false, false)
        .hits({4, 6, 7}).check(false, false);
    auto expected = Expect::hits({1, 3, 5});
    verify(Combine<AndNotFlow>(Blueprint::create_andnot_filter, child_list), expected);
    verify(Make<AndNotBlueprint>(child_list), expected);
}

TEST(FilterSearchTest, rank_filter) {
    auto child_list1 = Children().hits({1,2,3}).empty().full();
    auto child_list2 = Children().empty().hits({1,2,3}).full();
    auto child_list3 = Children().full().hits({1,2,3}).empty();
    auto adapter = [](const auto &children, Constraint constraint) {
                       return Blueprint::create_first_child_filter(children, constraint);
                   };
    verify(Combine<RankFlow>(adapter, child_list1), Expect::hits({1,2,3}));
    verify(Combine<RankFlow>(adapter, child_list2), Expect::empty());
    verify(Combine<RankFlow>(adapter, child_list3), Expect::full());
    verify(Make<RankBlueprint>(child_list1), Expect::hits({1,2,3}));
    verify(Make<RankBlueprint>(child_list2), Expect::empty());
    verify(Make<RankBlueprint>(child_list3), Expect::full());
}

TEST(FilterSearchTest, or_short_circuit) {
    auto child_list = Children()
        .hits({5, 10}).check(true, true)
        .full().check(true, true)
        .hits({3, 11}).check(true, true).dropped();
    verify(Combine<OrFlow>(Blueprint::create_or_filter, child_list),
           Expect::full());
}

TEST(FilterSearchTest, or_pruning) {
    auto child_list = Children()
        .empty().check(true, true)
        .empty().check(true, true)
        .empty().check(true, true);
    verify(Combine<OrFlow>(Blueprint::create_or_filter, child_list),
           Expect::empty());
}

TEST(FilterSearchTest, or_partial_pruning) {
    auto child_list = Children()
        .hits({5, 10}).check(true, true)
        .empty().check(true, true)
        .hits({3, 11}).check(true, true);
    verify(Combine<OrFlow>(Blueprint::create_or_filter, child_list),
           Expect::hits({3, 5, 10, 11}).child_count(2));
}

TEST(FilterSearchTest, and_short_circuit) {
    auto child_list = Children()
        .hits({1, 2, 3}).check(true, true)
        .empty().check(false, true)
        .hits({2, 3, 4}).check(false, true).dropped();
    verify(Combine<AndFlow>(Blueprint::create_and_filter, child_list),
           Expect::empty());
}

TEST(FilterSearchTest, and_pruning) {
    auto child_list = Children()
        .full().check(true, true)
        .full().check(false, true)
        .full().check(false, true);
    verify(Combine<AndFlow>(Blueprint::create_and_filter, child_list),
           Expect::full());
}

TEST(FilterSearchTest, and_partial_pruning) {
    auto child_list = Children()
        .hits({1, 2, 3}).check(true, true)
        .full().check(false, true)
        .hits({2, 3, 4}).check(false, true);
    verify(Combine<AndFlow>(Blueprint::create_and_filter, child_list),
           Expect::hits({2, 3}).child_count(2));
}

TEST(FilterSearchTest, andnot_positive_short_circuit) {
    auto child_list = Children()
        .empty().check(true, true)
        .hits({1, 2, 3}).check(false, false).dropped();
    verify(Combine<AndNotFlow>(Blueprint::create_andnot_filter, child_list),
           Expect::empty());
}

TEST(FilterSearchTest, andnot_negative_short_circuit) {
    auto child_list = Children()
        .hits({1, 2, 3}).check(true, true)
        .hits({1}).check(false, false)
        .full().check(false, false)
        .hits({3}).check(false, false).dropped();
    verify(Combine<AndNotFlow>(Blueprint::create_andnot_filter, child_list),
           Expect::empty());
}

TEST(FilterSearchTest, andnot_negative_pruning) {
    auto child_list = Children()
        .full().check(true, true)
        .empty().check(false, false)
        .empty().check(false, false)
        .empty().check(false, false);
    verify(Combine<AndNotFlow>(Blueprint::create_andnot_filter, child_list),
           Expect::full());
}

TEST(FilterSearchTest, andnot_partial_negative_pruning) {
    auto child_list = Children()
        .hits({1, 2, 3}).check(true, true)
        .hits({1}).check(false, false)
        .empty().check(false, false)
        .hits({3}).check(false, false);
    verify(Combine<AndNotFlow>(Blueprint::create_andnot_filter, child_list),
           Expect::hits({2}).child_count(3));
}

TEST(FilterSearchTest, first_or_child_can_be_partially_pruned) {
    auto child_list = Children()
        .empty().check(true, true)
        .hits({5, 10}).check(true, true)
        .hits({3, 11}).check(true, true);
    verify(Combine<OrFlow>(Blueprint::create_or_filter, child_list),
           Expect::hits({3, 5, 10, 11}).child_count(2));
}

TEST(FilterSearchTest, first_and_child_can_be_pruned) {
    auto child_list = Children()
        .full().check(true, true)
        .hits({1, 2, 3}).check(false, true)
        .hits({2, 3, 4}).check(false, true);
    verify(Combine<AndFlow>(Blueprint::create_and_filter, child_list),
           Expect::hits({2, 3}).child_count(2));
}

TEST(FilterSearchTest, first_negative_andnot_child_can_be_partially_pruned) {
    auto child_list = Children()
        .hits({1, 2, 3}).check(true, true)
        .empty().check(false, false)
        .hits({1}).check(false, false)
        .hits({3}).check(false, false);
    verify(Combine<AndNotFlow>(Blueprint::create_andnot_filter, child_list),
           Expect::hits({2}).child_count(3));
}

TEST(FilterSearchTest, need_atleast_one_child) {
    verify(Combine<AndFlow>(Blueprint::create_and_filter, Children().full()), Expect::full());
    verify(Combine<OrFlow>(Blueprint::create_or_filter, Children().empty()), Expect::empty());
    verify(Combine<AndNotFlow>(Blueprint::create_andnot_filter, Children().full()), Expect::full());
    EXPECT_THROW(verify(Combine<AndFlow>(Blueprint::create_and_filter, Children()), Expect::empty()),
                 vespalib::RequireFailedException);
    EXPECT_THROW(verify(Combine<OrFlow>(Blueprint::create_or_filter, Children()), Expect::empty()),
                 vespalib::RequireFailedException);
    EXPECT_THROW(verify(Combine<AndNotFlow>(Blueprint::create_andnot_filter, Children()), Expect::empty()),
                 vespalib::RequireFailedException);
}

GTEST_MAIN_RUN_ALL_TESTS()
