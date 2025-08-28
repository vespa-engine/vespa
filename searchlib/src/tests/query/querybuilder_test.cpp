// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for querybuilder.

#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchlib/query/tree/customtypevisitor.h>
#include <vespa/searchlib/query/tree/point.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchlib/query/query_term_decoder.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("querybuilder_test");
#include <vespa/searchlib/query/tree/querytreecreator.h>

using std::string_view;
using std::string;
using search::SimpleQueryStackDumpIterator;
using namespace search::query;

namespace {

template <class NodeTypes> void checkQueryTreeTypes(Node *node);

const string str[] = { "foo", "bar", "baz", "qux", "quux", "corge",
                       "grault", "garply", "waldo", "fred", "plugh" };
const string (&view)[11] = str;
const int32_t id[] = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };
const Weight weight[] = { Weight(1), Weight(2), Weight(3), Weight(4),
                          Weight(5), Weight(6), Weight(7), Weight(8),
                          Weight(9), Weight(10), Weight(11) };
const size_t distance = 4;
const string int1 = "42";
const string float1 = "3.14";
const Range range(32, 64);
const Point position{100, 100};
const int max_distance = 20;
const uint32_t x_aspect = 0;
const Location location(position, max_distance, x_aspect);

PredicateQueryTerm::UP getPredicateQueryTerm() {
    auto pqt = std::make_unique<PredicateQueryTerm>();
    pqt->addFeature("key", "value");
    pqt->addRangeFeature("key2", 42, 0xfff);
    return pqt;
}

template <class NodeTypes>
Node::UP createQueryTree() {
    QueryBuilder<NodeTypes> builder;
    builder.addAnd(13);
    {
        builder.addRank(2);
        {
            builder.addNear(2, distance);
            {
                builder.addStringTerm(str[0], view[0], id[0], weight[0]);
                builder.addSubstringTerm(str[1], view[1], id[1], weight[1]);
            }
            builder.addONear(2, distance);
            {
                builder.addSuffixTerm(str[2], view[2], id[2], weight[2]);
                builder.addPrefixTerm(str[3], view[3], id[3], weight[3]);
            }
        }
        builder.addOr(3);
        {
            builder.addPhrase(3, view[4], id[4], weight[4]);
            {
                builder.addStringTerm(str[4], view[4], id[4], weight[5]);
                builder.addStringTerm(str[5], view[5], id[5], weight[6]);
                builder.addStringTerm(str[6], view[6], id[6], weight[7]);
            }
            builder.addPhrase(2, view[4], id[4], weight[4])
                .setRanked(false);
            {
                builder.addStringTerm(str[4], view[4], id[4], weight[5]);
                builder.addStringTerm(str[5], view[5], id[5], weight[6]);
            }
            builder.addAndNot(2);
            {
                builder.addNumberTerm(int1, view[7], id[7], weight[7]);
                builder.addNumberTerm(float1, view[8], id[8], weight[8])
                    .setRanked(false);
            }
        }
        builder.addRangeTerm(range, view[9], id[9], weight[9]);
        builder.addLocationTerm(location, view[10], id[10], weight[10]);
        builder.addWeakAnd(2, 123, view[0]);
        {
            builder.addStringTerm(str[4], view[4], id[4], weight[4]);
            builder.addStringTerm(str[5], view[5], id[5], weight[5]);
        }
        builder.addPredicateQuery(getPredicateQueryTerm(), view[3], id[3], weight[3]);
        {
            auto & n = builder.addDotProduct(3, view[2], id[2], weight[2]);
            n.addTerm(str[3], weight[3]);
            n.addTerm(str[4], weight[4]);
            n.addTerm(str[5], weight[5]);
        }
        {
            auto & n = builder.addWandTerm(2, view[0], id[0], weight[0], 57, 67, 77.7);
            n.addTerm(str[1], weight[1]);
            n.addTerm(str[2], weight[2]);
        }
        builder.addRegExpTerm(str[5], view[5], id[5], weight[5]);
        builder.addSameElement(3, view[4], id[4], weight[4]);
        {
            builder.addStringTerm(str[4], view[4], id[4], weight[5]);
            builder.addStringTerm(str[5], view[5], id[5], weight[6]);
            builder.addStringTerm(str[6], view[6], id[6], weight[7]);
        }
        builder.add_nearest_neighbor_term("query_tensor", "doc_tensor", id[3], weight[5], 7, true, 33, 100100.25);
        builder.addAndNot(2);
        {
            builder.add_true_node();
            builder.add_false_node();
        }
        builder.addFuzzyTerm(str[5], view[5], id[5], weight[5], 3, 1, false);
    }
    Node::UP node = builder.build();
    assert(node.get());
    return node;
}

template <class TermType>
bool compareTerms(const TermType &expected, const TermType &actual) {
    bool retval = (expected == actual);
    EXPECT_TRUE(retval);
    return retval;
}
template <typename T>
bool compareTerms(const std::unique_ptr<T> &expected,
                  const std::unique_ptr<T> &actual) {
    bool retval = (*expected == *actual);
    EXPECT_TRUE(retval);
    return retval;
}

template <class Term>
bool checkTerm(const Term *term, const typename Term::Type &t, const string &f,
               int32_t i, Weight w, bool ranked = true,
               bool use_position_data = true) {
    EXPECT_TRUE(term != nullptr);
    if (term == nullptr) {
        return false;
    }
    bool result = true;
    EXPECT_TRUE(compareTerms(t, term->getTerm()));
    if (!compareTerms(t, term->getTerm())) {
        result = false;
    }
    EXPECT_EQ(f, term->getView());
    if (f != term->getView()) {
        result = false;
    }
    EXPECT_EQ(i, term->getId());
    if (i != term->getId()) {
        result = false;
    }
    EXPECT_EQ(w.percent(), term->getWeight().percent());
    if (w.percent() != term->getWeight().percent()) {
        result = false;
    }
    EXPECT_EQ(ranked, term->isRanked());
    if (ranked != term->isRanked()) {
        result = false;
    }
    EXPECT_EQ(use_position_data, term->usePositionData());
    if (use_position_data != term->usePositionData()) {
        result = false;
    }
    return result;
}

template <class NodeType>
NodeType*
as_node(Node* node)
{
    auto* result = dynamic_cast<NodeType*>(node);
    assert(result != nullptr);
    return result;
}

template <class NodeTypes>
void checkQueryTreeTypes(Node *node) {
    using And = typename NodeTypes::And;
    using AndNot = typename NodeTypes::AndNot;
    using NumberTerm = typename NodeTypes::NumberTerm;
    using Near = typename NodeTypes::Near;
    using ONear = typename NodeTypes::ONear;
    using SameElement = typename NodeTypes::SameElement;
    using Or = typename NodeTypes::Or;
    using Phrase = typename NodeTypes::Phrase;
    using PrefixTerm = typename NodeTypes::PrefixTerm;
    using RangeTerm = typename NodeTypes::RangeTerm;
    using Rank = typename NodeTypes::Rank;
    using StringTerm = typename NodeTypes::StringTerm;
    using SuffixTerm = typename NodeTypes::SuffixTerm;
    using LocationTerm = typename NodeTypes::LocationTerm;
    using DotProduct = typename NodeTypes::DotProduct;
    using WandTerm = typename NodeTypes::WandTerm;
    using WeakAnd = typename NodeTypes::WeakAnd;
    using PredicateQuery = typename NodeTypes::PredicateQuery;
    using RegExpTerm = typename NodeTypes::RegExpTerm;
    using TrueNode = typename NodeTypes::TrueQueryNode;
    using FalseNode = typename NodeTypes::FalseQueryNode;
    using FuzzyTerm = typename NodeTypes::FuzzyTerm;

    assert(node);
    auto* and_node = as_node<And>(node);
    EXPECT_EQ(13u, and_node->getChildren().size());

    auto* rank = as_node<Rank>(and_node->getChildren()[0]);
    EXPECT_EQ(2u, rank->getChildren().size());

    auto* near = as_node<Near>(rank->getChildren()[0]);
    EXPECT_EQ(2u, near->getChildren().size());
    EXPECT_EQ(distance, near->getDistance());
    auto* string_term = as_node<StringTerm>(near->getChildren()[0]);
    EXPECT_TRUE(checkTerm(string_term, str[0], view[0], id[0], weight[0]));
    auto* substring_term = as_node<SubstringTerm>(near->getChildren()[1]);
    EXPECT_TRUE(checkTerm(substring_term, str[1], view[1], id[1], weight[1]));

    auto* onear = as_node<ONear>(rank->getChildren()[1]);
    EXPECT_EQ(2u, onear->getChildren().size());
    EXPECT_EQ(distance, onear->getDistance());
    auto* suffix_term = as_node<SuffixTerm>(onear->getChildren()[0]);
    EXPECT_TRUE(checkTerm(suffix_term, str[2], view[2], id[2], weight[2]));
    auto* prefix_term = as_node<PrefixTerm>(onear->getChildren()[1]);
    EXPECT_TRUE(checkTerm(prefix_term, str[3], view[3], id[3], weight[3]));

    auto* or_node = as_node<Or>(and_node->getChildren()[1]);
    EXPECT_EQ(3u, or_node->getChildren().size());

    auto* phrase = as_node<Phrase>(or_node->getChildren()[0]);
    EXPECT_TRUE(phrase->isRanked());
    EXPECT_EQ(weight[4].percent(), phrase->getWeight().percent());
    EXPECT_EQ(3u, phrase->getChildren().size());
    string_term = as_node<StringTerm>(phrase->getChildren()[0]);
    EXPECT_TRUE(checkTerm(string_term, str[4], view[4], id[4], weight[4]));
    string_term = as_node<StringTerm>(phrase->getChildren()[1]);
    EXPECT_TRUE(checkTerm(string_term, str[5], view[5], id[5], weight[4]));
    string_term = as_node<StringTerm>(phrase->getChildren()[2]);
    EXPECT_TRUE(checkTerm(string_term, str[6], view[6], id[6], weight[4]));

    phrase = as_node<Phrase>(or_node->getChildren()[1]);
    EXPECT_TRUE(!phrase->isRanked());
    EXPECT_EQ(weight[4].percent(), phrase->getWeight().percent());
    EXPECT_EQ(2u, phrase->getChildren().size());
    string_term = as_node<StringTerm>(phrase->getChildren()[0]);
    EXPECT_TRUE(checkTerm(string_term, str[4], view[4], id[4], weight[4]));
    string_term = as_node<StringTerm>(phrase->getChildren()[1]);
    EXPECT_TRUE(checkTerm(string_term, str[5], view[5], id[5], weight[4]));

    auto* and_not = as_node<AndNot>(or_node->getChildren()[2]);
    EXPECT_EQ(2u, and_not->getChildren().size());
    auto* integer_term = as_node<NumberTerm>(and_not->getChildren()[0]);
    EXPECT_TRUE(checkTerm(integer_term, int1, view[7], id[7], weight[7]));
    auto* float_term = as_node<NumberTerm>(and_not->getChildren()[1]);
    EXPECT_TRUE(checkTerm(float_term, float1, view[8], id[8], weight[8], false));

    auto* range_term = as_node<RangeTerm>(and_node->getChildren()[2]);
    EXPECT_TRUE(checkTerm(range_term, range, view[9], id[9], weight[9]));

    auto* loc_term = as_node<LocationTerm>(and_node->getChildren()[3]);
    EXPECT_TRUE(checkTerm(loc_term, location, view[10], id[10], weight[10]));

    auto* wand = as_node<WeakAnd>(and_node->getChildren()[4]);
    EXPECT_EQ(123u, wand->getTargetNumHits());
    EXPECT_EQ(2u, wand->getChildren().size());
    string_term = as_node<StringTerm>(wand->getChildren()[0]);
    EXPECT_TRUE(checkTerm(string_term, str[4], view[4], id[4], weight[4]));
    string_term = as_node<StringTerm>(wand->getChildren()[1]);
    EXPECT_TRUE(checkTerm(string_term, str[5], view[5], id[5], weight[5]));

    auto* predicateQuery = as_node<PredicateQuery>(and_node->getChildren()[5]);
    auto pqt = std::make_unique<PredicateQueryTerm>();
    EXPECT_TRUE(checkTerm(predicateQuery, getPredicateQueryTerm(), view[3], id[3], weight[3]));

    auto* dotProduct = as_node<DotProduct>(and_node->getChildren()[6]);
    EXPECT_EQ(3u, dotProduct->getNumTerms());

    {
        const auto &w1 = dotProduct->getAsString(0);
        EXPECT_EQ(w1.first, str[3]);
        EXPECT_TRUE(w1.second == weight[3]);
        const auto &w2 = dotProduct->getAsString(1);
        EXPECT_EQ(w2.first, str[4]);
        EXPECT_TRUE(w2.second == weight[4]);
        const auto &w3 = dotProduct->getAsString(2);
        EXPECT_EQ(w3.first, str[5]);
        EXPECT_TRUE(w3.second == weight[5]);
    }

    auto* wandTerm = as_node<WandTerm>(and_node->getChildren()[7]);
    EXPECT_EQ(57u, wandTerm->getTargetNumHits());
    EXPECT_EQ(67, wandTerm->getScoreThreshold());
    EXPECT_EQ(77.7, wandTerm->getThresholdBoostFactor());
    EXPECT_EQ(2u, wandTerm->getNumTerms());
    {
        const auto &w1 = wandTerm->getAsString(0);
        EXPECT_EQ(w1.first, str[1]);
        EXPECT_TRUE(w1.second == weight[1]);
        const auto &w2 = wandTerm->getAsString(1);
        EXPECT_EQ(w2.first, str[2]);
        EXPECT_TRUE(w2.second == weight[2]);
    }

    auto* regexp_term = as_node<RegExpTerm>(and_node->getChildren()[8]);
    EXPECT_TRUE(checkTerm(regexp_term, str[5], view[5], id[5], weight[5]));

    auto* same = as_node<SameElement>(and_node->getChildren()[9]);
    EXPECT_EQ(view[4], same->getView());
    EXPECT_EQ(3u, same->getChildren().size());
    string_term = as_node<StringTerm>(same->getChildren()[0]);
    EXPECT_TRUE(checkTerm(string_term, str[4], view[4], id[4], weight[5]));
    string_term = as_node<StringTerm>(same->getChildren()[1]);
    EXPECT_TRUE(checkTerm(string_term, str[5], view[5], id[5], weight[6]));
    string_term = as_node<StringTerm>(same->getChildren()[2]);
    EXPECT_TRUE(checkTerm(string_term, str[6], view[6], id[6], weight[7]));

    auto* nearest_neighbor = as_node<NearestNeighborTerm>(and_node->getChildren()[10]);
    EXPECT_EQ("query_tensor", nearest_neighbor->get_query_tensor_name());
    EXPECT_EQ("doc_tensor", nearest_neighbor->getView());
    EXPECT_EQ(id[3], nearest_neighbor->getId());
    EXPECT_EQ(weight[5].percent(), nearest_neighbor->getWeight().percent());
    EXPECT_EQ(7u, nearest_neighbor->get_target_num_hits());

    and_not = as_node<AndNot>(and_node->getChildren()[11]);
    EXPECT_EQ(2u, and_not->getChildren().size());
    auto* true_node = as_node<TrueNode>(and_not->getChildren()[0]);
    auto* false_node = as_node<FalseNode>(and_not->getChildren()[1]);
    EXPECT_TRUE(true_node);
    EXPECT_TRUE(false_node);

    auto* fuzzy_term = as_node<FuzzyTerm>(and_node->getChildren()[12]);
    EXPECT_TRUE(checkTerm(fuzzy_term, str[5], view[5], id[5], weight[5]));
    EXPECT_EQ(3u, fuzzy_term->max_edit_distance());
    EXPECT_EQ(1u, fuzzy_term->prefix_lock_length());
}

struct AbstractTypes {
    using And = search::query::And;
    using AndNot = search::query::AndNot;
    using NumberTerm = search::query::NumberTerm;
    using LocationTerm = search::query::LocationTerm;
    using Near = search::query::Near;
    using ONear = search::query::ONear;
    using SameElement = search::query::SameElement;
    using Or = search::query::Or;
    using Phrase = search::query::Phrase;
    using PrefixTerm = search::query::PrefixTerm;
    using RangeTerm = search::query::RangeTerm;
    using Rank = search::query::Rank;
    using StringTerm = search::query::StringTerm;
    using SubstringTerm = search::query::SubstringTerm;
    using SuffixTerm = search::query::SuffixTerm;
    using WeightedSetTerm = search::query::WeightedSetTerm;
    using DotProduct = search::query::DotProduct;
    using WandTerm = search::query::WandTerm;
    using WeakAnd = search::query::WeakAnd;
    using PredicateQuery = search::query::PredicateQuery;
    using RegExpTerm = search::query::RegExpTerm;
    using TrueQueryNode = search::query::TrueQueryNode;
    using FalseQueryNode = search::query::FalseQueryNode;
    using FuzzyTerm = search::query::FuzzyTerm;
};

// Builds a tree with simplequery and checks that the results have the
// correct abstract types.
TEST(QueryBuilderTest, require_that_Query_Trees_Can_Be_Built) {
    Node::UP node = createQueryTree<SimpleQueryNodeTypes>();
    checkQueryTreeTypes<AbstractTypes>(node.get());
}

// Builds a tree with simplequery and checks that the results have the
// correct concrete types.
TEST(QueryBuilderTest, require_that_Simple_Query_Trees_Can_Be_Built) {
    Node::UP node = createQueryTree<SimpleQueryNodeTypes>();
    checkQueryTreeTypes<SimpleQueryNodeTypes>(node.get());
}

struct MyAnd : And {
    ~MyAnd() override;
};

struct MyAndNot : AndNot {
    ~MyAndNot() override;
};

struct MyEquiv : Equiv {
    MyEquiv(int32_t i, Weight w) : Equiv(i, w) {}
    ~MyEquiv() override;
};

struct MyNear : Near {
    explicit MyNear(size_t dist) : Near(dist) {}
    ~MyNear() override;
};

struct MyONear : ONear {
    explicit MyONear(size_t dist) : ONear(dist) {}
    ~MyONear() override;
};

struct MyWeakAnd : WeakAnd {
    MyWeakAnd(uint32_t minHits, const string & v) : WeakAnd(minHits, v) {}
    ~MyWeakAnd() override;
};

struct MyOr : Or {
    ~MyOr() override;
};

struct MyPhrase : Phrase {
    MyPhrase(const string & f, int32_t i, Weight w) : Phrase(f, i, w) {}
    ~MyPhrase() override;
};

struct MySameElement : SameElement {
    MySameElement(const string & f, int32_t i, Weight w) : SameElement(f, i, w) {}
    ~MySameElement() override;
};

struct MyWeightedSetTerm : WeightedSetTerm {
    MyWeightedSetTerm(uint32_t n, const string & f, int32_t i, Weight w) : WeightedSetTerm(n, f, i, w) {}
    ~MyWeightedSetTerm() override;
};

struct MyDotProduct : DotProduct {
    MyDotProduct(uint32_t n, const string & f, int32_t i, Weight w) : DotProduct(n, f, i, w) {}
    ~MyDotProduct() override;
};

struct MyWandTerm : WandTerm {
    MyWandTerm(uint32_t n, const string & f, int32_t i, Weight w, uint32_t targetNumHits,
               int64_t scoreThreshold, double thresholdBoostFactor)
        : WandTerm(n, f, i, w, targetNumHits, scoreThreshold, thresholdBoostFactor) {}
    ~MyWandTerm() override;
};

struct MyRank : Rank {
    ~MyRank() override;
};

struct MyNumberTerm : NumberTerm {
    MyNumberTerm(Type t, const string & f, int32_t i, Weight w)
        : NumberTerm(t, f, i, w) {
    }
    ~MyNumberTerm() override;
};

struct MyLocationTerm : LocationTerm {
    MyLocationTerm(const Type &t, const string & f, int32_t i, Weight w)
        : LocationTerm(t, f, i, w) {
    }
    ~MyLocationTerm() override;
};

struct MyPrefixTerm : PrefixTerm {
    MyPrefixTerm(const Type &t, const string & f, int32_t i, Weight w)
        : PrefixTerm(t, f, i, w) {
    }
    ~MyPrefixTerm() override;
};

struct MyRangeTerm : RangeTerm {
    MyRangeTerm(const Type &t, const string &f, int32_t i, Weight w)
        : RangeTerm(t, f, i, w) {
    }
    ~MyRangeTerm() override;
};

struct MyStringTerm : StringTerm {
    MyStringTerm(const Type &t, const string & f, int32_t i, Weight w)
        : StringTerm(t, f, i, w) {
    }
    ~MyStringTerm() override;
};

struct MySubstringTerm : SubstringTerm {
    MySubstringTerm(const Type &t, const string & f, int32_t i, Weight w)
        : SubstringTerm(t, f, i, w) {
    }
    ~MySubstringTerm() override;
};

struct MySuffixTerm : SuffixTerm {
    MySuffixTerm(const Type &t, const string & f, int32_t i, Weight w)
        : SuffixTerm(t, f, i, w) {
    }
    ~MySuffixTerm() override;
};

struct MyPredicateQuery : PredicateQuery {
    MyPredicateQuery(Type &&t, const string & f, int32_t i, Weight w)
        : PredicateQuery(std::move(t), f, i, w) {
    }
    ~MyPredicateQuery() override;
};

struct MyRegExpTerm : RegExpTerm {
    MyRegExpTerm(const Type &t, const string & f, int32_t i, Weight w)
        : RegExpTerm(t, f, i, w) {
    }
    ~MyRegExpTerm() override;
};

struct MyNearestNeighborTerm : NearestNeighborTerm {
    MyNearestNeighborTerm(std::string_view query_tensor_name, const string & field_name,
                          int32_t i, Weight w, uint32_t target_num_hits,
                          bool allow_approximate, uint32_t explore_additional_hits,
                          double distance_threshold)
      : NearestNeighborTerm(query_tensor_name, field_name, i, w, target_num_hits, allow_approximate, explore_additional_hits, distance_threshold)
    {}
    ~MyNearestNeighborTerm() override;
};

struct MyTrue : TrueQueryNode {
    ~MyTrue() override;
};

struct MyFalse : FalseQueryNode {
    ~MyFalse() override;
};

struct MyFuzzyTerm : FuzzyTerm {
    MyFuzzyTerm(const Type &t, const string &f, int32_t i, Weight w,
                uint32_t m, uint32_t p, bool prefix_match)
        : FuzzyTerm(t, f, i, w, m, p, prefix_match)
    {
    }
    ~MyFuzzyTerm() override;
};

struct MyInTerm : InTerm {
    MyInTerm(std::unique_ptr<TermVector> terms, MultiTerm::Type type,
             const string& f, int32_t i, Weight w)
        : InTerm(std::move(terms), type, f, i, w)
    {
    }
    ~MyInTerm() override;
};

MyAnd::~MyAnd() = default;

MyAndNot::~MyAndNot() = default;

MyEquiv::~MyEquiv() = default;

MyNear::~MyNear() = default;

MyONear::~MyONear() = default;

MyWeakAnd::~MyWeakAnd() = default;

MyOr::~MyOr() = default;

MyPhrase::~MyPhrase() = default;

MySameElement::~MySameElement() = default;

MyWeightedSetTerm::~MyWeightedSetTerm() = default;

MyDotProduct::~MyDotProduct() = default;

MyWandTerm::~MyWandTerm() = default;

MyRank::~MyRank() = default;

MyNumberTerm::~MyNumberTerm() = default;

MyLocationTerm::~MyLocationTerm() = default;

MyPrefixTerm::~MyPrefixTerm() = default;

MyRangeTerm::~MyRangeTerm() = default;

MyStringTerm::~MyStringTerm() = default;

MySubstringTerm::~MySubstringTerm() = default;

MySuffixTerm::~MySuffixTerm() = default;

MyPredicateQuery::~MyPredicateQuery() = default;

MyRegExpTerm::~MyRegExpTerm() = default;

MyNearestNeighborTerm::~MyNearestNeighborTerm() = default;

MyTrue::~MyTrue() = default;

MyFalse::~MyFalse() = default;

MyFuzzyTerm::~MyFuzzyTerm() = default;

MyInTerm::~MyInTerm() = default;

struct MyQueryNodeTypes {
    using And = MyAnd;
    using AndNot = MyAndNot;
    using Equiv = MyEquiv;
    using NumberTerm = MyNumberTerm;
    using LocationTerm = MyLocationTerm;
    using Near = MyNear;
    using ONear = MyONear;
    using Or = MyOr;
    using Phrase = MyPhrase;
    using SameElement = MySameElement;
    using PrefixTerm = MyPrefixTerm;
    using RangeTerm = MyRangeTerm;
    using Rank = MyRank;
    using StringTerm = MyStringTerm;
    using SubstringTerm = MySubstringTerm;
    using SuffixTerm = MySuffixTerm;
    using WeakAnd = MyWeakAnd;
    using WeightedSetTerm = MyWeightedSetTerm;
    using DotProduct = MyDotProduct;
    using WandTerm = MyWandTerm;
    using PredicateQuery = MyPredicateQuery;
    using RegExpTerm = MyRegExpTerm;
    using NearestNeighborTerm = MyNearestNeighborTerm;
    using TrueQueryNode = MyTrue;
    using FalseQueryNode = MyFalse;
    using FuzzyTerm = MyFuzzyTerm;
    using InTerm = MyInTerm;
};

TEST(QueryBuilderTest, require_that_Custom_Query_Trees_Can_Be_Built) {
    Node::UP node = createQueryTree<MyQueryNodeTypes>();
    checkQueryTreeTypes<MyQueryNodeTypes>(node.get());
}

TEST(QueryBuilderTest, require_that_Invalid_Trees_Cannot_Be_Built) {
    // Incomplete tree.
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addAnd(1);
    ASSERT_TRUE(!builder.build().get());
    EXPECT_EQ("QueryBuilderBase::build: QueryBuilder got invalid node structure. _nodes are not empty.", builder.error());

    // Adding a node after build() and before reset() is a no-op.
    builder.addStringTerm(str[0], view[0], id[0], weight[0]);
    ASSERT_TRUE(!builder.build().get());
    EXPECT_EQ("QueryBuilderBase::build: QueryBuilder got invalid node structure. _nodes are not empty.", builder.error());

    builder.reset();
    EXPECT_TRUE(builder.error().empty());

    // Too many nodes.
    builder.addAnd(1);
    builder.addStringTerm(str[0], view[0], id[0], weight[0]);
    builder.addStringTerm(str[1], view[1], id[1], weight[1]);
    ASSERT_TRUE(!builder.build().get());
    EXPECT_EQ("QueryBuilderBase::addCompleteNode: QueryBuilder got invalid node structure."
                 " Incomming node is 'search::query::SimpleStringTerm', while root is non-null('search::query::SimpleAnd')",
                 builder.error());

    // Adding an intermediate node after build() is also a no-op.
    builder.addAnd(1);
    ASSERT_TRUE(!builder.build().get());
    EXPECT_EQ("QueryBuilderBase::addCompleteNode: QueryBuilder got invalid node structure."
                 " Incomming node is 'search::query::SimpleStringTerm', while root is non-null('search::query::SimpleAnd')",
                 builder.error());
}

TEST(QueryBuilderTest, require_that_Rank_Can_Be_Turned_Off) {
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addAnd(3);
    builder.addStringTerm(str[0], view[0], id[0], weight[0]);
    builder.addSubstringTerm(str[1], view[1], id[1], weight[1])
        .setRanked(false);
    builder.addPhrase(2, view[2], id[2], weight[2])
        .setRanked(false);
    {
        builder.addStringTerm(str[2], view[2], id[3], weight[3]);
        builder.addStringTerm(str[3], view[2], id[4], weight[4]);
    }

    Node::UP node = builder.build();
    ASSERT_TRUE(!builder.hasError());
    Intermediate *intermediate = dynamic_cast<Intermediate *>(node.get());
    ASSERT_TRUE(intermediate);
    ASSERT_TRUE(intermediate->getChildren().size() == 3);
    Term *term = dynamic_cast<Term *>(intermediate->getChildren()[0]);
    ASSERT_TRUE(term);
    EXPECT_TRUE(term->isRanked());
    term = dynamic_cast<Term *>(intermediate->getChildren()[1]);
    ASSERT_TRUE(term);
    EXPECT_TRUE(!term->isRanked());
    Phrase *phrase = dynamic_cast<Phrase *>(intermediate->getChildren()[2]);
    ASSERT_TRUE(phrase);
    EXPECT_TRUE(!phrase->isRanked());
}

TEST(QueryBuilderTest, require_that_Using_Position_Data_Can_Be_Turned_Off) {
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addAnd(2);
    builder.addStringTerm(str[0], view[0], id[0], weight[0]).setPositionData(false);
    builder.addPhrase(2, view[1], id[1], weight[1]).setPositionData(false);
    builder.addStringTerm(str[2], view[1], id[2], weight[2]);
    builder.addStringTerm(str[3], view[1], id[3], weight[3]);

    Node::UP node = builder.build();
    ASSERT_TRUE(!builder.hasError());
    Intermediate * andNode = dynamic_cast<Intermediate *>(node.get());
    ASSERT_TRUE(andNode != nullptr);
    ASSERT_TRUE(andNode->getChildren().size() == 2);
    Term * term = dynamic_cast<Term *>(andNode->getChildren()[0]);
    ASSERT_TRUE(term != nullptr);
    EXPECT_TRUE(!term->usePositionData());
    Phrase * phrase = dynamic_cast<Phrase *>(andNode->getChildren()[1]);
    ASSERT_TRUE(phrase != nullptr);
    EXPECT_TRUE(!phrase->usePositionData());
}

TEST(QueryBuilderTest, require_that_Weight_Override_Works_Across_Multiple_Levels) {
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addPhrase(2, view[0], id[0], weight[0]);

    SimpleStringTerm &string_term_1 = builder.addStringTerm(str[1], view[1], id[1], weight[1]);
    EXPECT_EQ(weight[0].percent(), string_term_1.getWeight().percent());

    builder.addAnd(2);
    SimpleStringTerm &string_term_2 = builder.addStringTerm(str[2], view[2], id[2], weight[2]);
    EXPECT_EQ(weight[0].percent(), string_term_2.getWeight().percent());
}

TEST(QueryBuilderTest, require_that_Query_Tree_Creator_Can_Replicate_Queries) {
    Node::UP node = createQueryTree<SimpleQueryNodeTypes>();
    Node::UP new_node = QueryTreeCreator<MyQueryNodeTypes>::replicate(*node);

    checkQueryTreeTypes<SimpleQueryNodeTypes>(node.get());
    checkQueryTreeTypes<MyQueryNodeTypes>(new_node.get());
}

TEST(QueryBuilderTest, require_that_Query_Tree_Creator_Can_Create_Queries_From_Stack) {
    Node::UP node = createQueryTree<MyQueryNodeTypes>();
    string stackDump = StackDumpCreator::create(*node);

    SimpleQueryStackDumpIterator iterator(stackDump);

    Node::UP new_node = QueryTreeCreator<SimpleQueryNodeTypes>::create(iterator);
    checkQueryTreeTypes<SimpleQueryNodeTypes>(new_node.get());
}

TEST(QueryBuilderTest, require_that_All_Range_Syntaxes_Work) {

    Range range0("[2,42.1]");
    Range range1(">10");
    Range range2("<45.23");

    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addAnd(3);
    builder.addRangeTerm(range0, "view", 0, Weight(0));
    builder.addRangeTerm(range1, "view", 0, Weight(0));
    builder.addRangeTerm(range2, "view", 0, Weight(0));
    Node::UP node = builder.build();

    string stackDump = StackDumpCreator::create(*node);
    SimpleQueryStackDumpIterator iterator(stackDump);

    Node::UP new_node = QueryTreeCreator<SimpleQueryNodeTypes>::create(iterator);
    And *and_node = dynamic_cast<And *>(new_node.get());
    ASSERT_TRUE(and_node);
    EXPECT_EQ(3u, and_node->getChildren().size());

    auto range_term = dynamic_cast<RangeTerm *>(and_node->getChildren()[0]);
    ASSERT_TRUE(range_term);
    EXPECT_TRUE(range0 == range_term->getTerm());

    range_term = dynamic_cast<RangeTerm *>(and_node->getChildren()[1]);
    ASSERT_TRUE(range_term);
    EXPECT_TRUE(range1 == range_term->getTerm());

    range_term = dynamic_cast<RangeTerm *>(and_node->getChildren()[2]);
    ASSERT_TRUE(range_term);
    EXPECT_TRUE(range2 == range_term->getTerm());
}

TEST(QueryBuilderTest, fuzzy_node_can_be_created) {
    for (bool prefix_match : {false, true}) {
        QueryBuilder<SimpleQueryNodeTypes> builder;
        builder.addFuzzyTerm("term", "view", 0, Weight(0), 3, 1, prefix_match);
        Node::UP node = builder.build();

        string stackDump = StackDumpCreator::create(*node);
        {
            SimpleQueryStackDumpIterator iterator(stackDump);
            Node::UP new_node = QueryTreeCreator<SimpleQueryNodeTypes>::create(iterator);
            auto *fuzzy_node = as_node<FuzzyTerm>(new_node.get());
            EXPECT_EQ(3u, fuzzy_node->max_edit_distance());
            EXPECT_EQ(1u, fuzzy_node->prefix_lock_length());
            EXPECT_EQ(prefix_match, fuzzy_node->prefix_match());
        }
        {
            search::QueryTermSimple::UP queryTermSimple = search::QueryTermDecoder::decodeTerm(stackDump);
            EXPECT_EQ(3u, queryTermSimple->fuzzy_max_edit_distance());
            EXPECT_EQ(1u, queryTermSimple->fuzzy_prefix_lock_length());
            EXPECT_EQ(prefix_match, queryTermSimple->fuzzy_prefix_match());
        }
    }
}

TEST(QueryBuilderTest, require_that_empty_intermediate_node_can_be_added) {
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addAnd(0);
    Node::UP node = builder.build();
    ASSERT_TRUE(node.get());

    string stackDump = StackDumpCreator::create(*node);
    SimpleQueryStackDumpIterator iterator(stackDump);

    Node::UP new_node = QueryTreeCreator<SimpleQueryNodeTypes>::create(iterator);
    And *and_node = dynamic_cast<And *>(new_node.get());
    ASSERT_TRUE(and_node);
    EXPECT_EQ(0u, and_node->getChildren().size());
}

TEST(QueryBuilderTest, control_size_of_SimpleQueryStackDumpIterator) {
    EXPECT_EQ(128u, sizeof(SimpleQueryStackDumpIterator));
}

TEST(QueryBuilderTest, test_query_parsing_error) {
    const char * STACK =
         "\001\002\001\003\000\005\002\004\001\034F\001\002\004term\004\004term\002dx\004\004term\002ifD\002\004term\001xD\003\004term\002dxE\004\004term\001\060F\005\002\004term"
         "\004\004term\006radius\004\004term\002ifD\006\004term\001xD\a\004term\004sizeE\b\004term\001\060D\t\004term\001xF\n\002\004term\004\004term\002dx\004\004term\002ifD\v\004term"
         "\001xD\f\004term\004sizeE\r\004term\001\060D\016\004term\002dxD\017\004term\004sizeE\020\004term\001\060F\021\002\004term\004\004term\006radius\004\004term\002ifD\022\004term"
         "\001yD\023\004term\001yF\024\002\004term\004\004term\002dy\004\004term\002ifD\025\004term\001yD\026\004term\002dyE\027\004term\001\060F\030\002\004term\004\004term\006radius"
         "\004\004term\002ifD\031\004term\001yD\032\004term\004sizeE\033\004term\001\061\004\001 F\034\002\004term\004\004term\001\061\004\004term\001xF\035\002\004term\004\004term"
         "\001\061\004\004term\001xF\036\002\004term\004\004term\001\061\004\004term\001y\002\004\001\034F\037\002\016term_variation\004\016term_variation\002dx\004\016term_variation"
         "\002ifD \016term_variation\001xD!\016term_variation\002dxE\"\016term_variation\001\060F#\002\016term_variation\004\016term_variation\006radius\004\016term_variation"
         "\002ifD$\016term_variation\001xD%\016term_variation\004sizeE&\016term_variation\001\060D'\016term_variation\001xF(\002\016term_variation\004\016term_variation"
         "\002dx\004\016term_variation\002ifD)\016term_variation\001xD*\016term_variation\004sizeE+\016term_variation\001\060D,\016term_variation\002dxD-\016term_variation\004size"
         "E.\016term_variation\001\060F/\002\016term_variation\004\016term_variation\006radius\004\016term_variation\002ifD0\016term_variation\001yD1\016term_variation"
         "\001yF2\002\016term_variation\004\016term_variation\002dy\004\016term_variation\002ifD3\016term_variation\001yD4\016term_variation\002dyE5\016term_variation"
         "\001\060F6\002\016term_variation\004\016term_variation\006radius\004\016term_variation\002ifD7\016term_variation\001yD8\016term_variation\004sizeE9\016term_variation"
         "\001\061\004\001 F:\002\016term_variation\004\016term_variation\001\061\004\016term_variation\001xF;\002\016term_variation\004\016term_variation\001\061\004\016term_variation"
         "\001xF<\002\016term_variation\004\016term_variation\001\061\004\016term_variation\001yD=\000\tvariation\002\004\001\034F>\002\004term\004\004term\002dx\004\004term\002ifD?\004term"
         "\001xD\200@\004term\002dxE\200A\004term\001\060F\200B\002\004term\004\004term\006radius\004\004term\002ifD\200C\004term\001xD\200D\004term\004sizeE\200E\004term\001\060D\200F\004term"
         "\001xF\200G\002\004term\004\004term\002dx\004\004term\002ifD\200H\004term\001xD\200I\004term\004sizeE\200J\004term\001\060D\200K\004term\002dxD\200L\004term\004sizeE\200M\004term"
         "\001\060F\200N\002\004term\004\004term\006radius\004\004term\002ifD\200O\004term\001yD\200P\004term\001yF\200Q\002\004term\004\004term\002dy\004\004term\002ifD\200R\004term"
         "\001yD\200S\004term\002dyE\200T\004term\001\060F\200U\002\004term\004\004term\006radius\004\004term\002ifD\200V\004term\001yD\200W\004term\004sizeE\200X\004term"
         "\001\061\004\001 F\200Y\002\004term\004\004term\001\061\004\004term\001xF\200Z\002\004term\004\004term\001\061\004\004term\001xF\200[\002\004term\004\004term\001\061\004\004term"
         "\001y\002\004\001\034F\200\\\002\016term_variation\004\016term_variation\002dx\004\016term_variation\002ifD\200]\016term_variation\001xD\200^\016term_variation"
         "\002dxE\200_\016term_variation\001\060F\200`\002\016term_variation\004\016term_variation\006radius\004\016term_variation\002ifD\200a\016term_variation\001xD\200b\016term_variation"
         "\004sizeE\200c\016term_variation\001\060D\200d\016term_variation\001xF\200e\002\016term_variation\004\016term_variation\002dx\004\016term_variation\002ifD\200f\016term_variation"
         "\001xD\200g\016term_variation\004sizeE\200h\016term_variation\001\060D\200i\016term_variation\002dxD\200j\016term_variation\004sizeE\200k\016term_variation"
         "\001\060F\200l\002\016term_variation\004\016term_variation\006radius\004\016term_variation\002ifD\200m\016term_variation\001yD\200n\016term_variation\001yF\200o\002\016term_variation"
         "\004\016term_variation\002dy\004\016term_variation\002ifD\200p\016term_variation\001yD\200q\016term_variation\002dyE\200r\016term_variation\001\060F\200s\002\016term_variation"
         "\004\016term_variation\006radius\004\016term_variation\002ifD\200t\016term_variation\001yD\200u\016term_variation\004sizeE\200v\016term_variation"
         "\001\061\004\001 F\200w\002\016term_variation\004\016term_variation\001\061\004\016term_variation\001xF\200x\002\016term_variation\004\016term_variation\001\061\004\016term_variation"
         "\001xF\200y\002\016term_variation\004\016term_variation\001\061\004\016term_variation\001yĀz\n\vsource_lang\002jaĀ{\n\vtarget_lang\002en\000\002Ā|\v\alicense"
         "\017countrycode_allĀ}\v\alicense\016countrycode_tw";
    string stackDump(STACK, 2936);
    SimpleQueryStackDumpIterator iterator(stackDump);
    Node::UP new_node = QueryTreeCreator<SimpleQueryNodeTypes>::create(iterator);
    EXPECT_FALSE(new_node);
}

class SimpleMultiTerm : public MultiTerm {
public:
    SimpleMultiTerm(size_t numTerms) : MultiTerm(numTerms) {}
    ~SimpleMultiTerm() override;
    void accept(QueryVisitor & ) override { }
};

SimpleMultiTerm::~SimpleMultiTerm() = default;

TEST(QueryBuilderTest, initial_state_of_MultiTerm) {
    SimpleMultiTerm mt(7);
    EXPECT_EQ(7u, mt.getNumTerms());
    EXPECT_TRUE(MultiTerm::Type::UNKNOWN == mt.getType());
}

void
verify_multiterm_get(const MultiTerm & mt) {
    EXPECT_EQ(7u, mt.getNumTerms());
    for (int64_t i(0); i < mt.getNumTerms(); i++) {
        auto v = mt.getAsInteger(i);
        EXPECT_EQ(v.first, i-3);
        EXPECT_EQ(v.second.percent(), i-4);
    }
    for (int64_t i(0); i < mt.getNumTerms(); i++) {
        auto v = mt.getAsString(i);
        char buf[24];
        auto res = std::to_chars(buf, buf + sizeof(buf), i-3);
        EXPECT_EQ(v.first, std::string_view(buf, res.ptr - buf));
        EXPECT_EQ(v.second.percent(), i-4);
    }
}

TEST(QueryBuilderTest, add_and_get_of_integer_MultiTerm) {
    SimpleMultiTerm mt(7);
    for (int64_t i(0); i < mt.getNumTerms(); i++) {
        mt.addTerm(i-3, Weight(i-4));
    }
    EXPECT_TRUE(MultiTerm::Type::INTEGER == mt.getType());
    verify_multiterm_get(mt);
}

TEST(QueryBuilderTest, add_and_get_of_string_MultiTerm) {
    SimpleMultiTerm mt(7);
    for (int64_t i(0); i < mt.getNumTerms(); i++) {
        char buf[24];
        auto res = std::to_chars(buf, buf + sizeof(buf), i-3);
        mt.addTerm(std::string_view(buf, res.ptr - buf), Weight(i-4));
    }
    EXPECT_TRUE(MultiTerm::Type::STRING == mt.getType());
    verify_multiterm_get(mt);
}

TEST(QueryBuilderTest, first_string_then_integer_MultiTerm) {
    SimpleMultiTerm mt(7);
    mt.addTerm("-3", Weight(-4));
    for (int64_t i(1); i < mt.getNumTerms(); i++) {
        mt.addTerm(i-3, Weight(i-4));
    }
    EXPECT_TRUE(MultiTerm::Type::STRING == mt.getType());
    verify_multiterm_get(mt);
}

TEST(QueryBuilderTest, first_integer_then_string_MultiTerm) {
    SimpleMultiTerm mt(7);
    mt.addTerm(-3, Weight(-4));
    EXPECT_TRUE(MultiTerm::Type::INTEGER == mt.getType());
    for (int64_t i(1); i < mt.getNumTerms(); i++) {
        char buf[24];
        auto res = std::to_chars(buf, buf + sizeof(buf), i-3);
        mt.addTerm(std::string_view(buf, res.ptr - buf), Weight(i-4));
    }
    EXPECT_TRUE(MultiTerm::Type::STRING == mt.getType());
    verify_multiterm_get(mt);
}

namespace {

std::vector<std::string> in_strings = { "this", "is", "a", "test" };

std::vector<int64_t> in_integers = { 24, INT64_C(93000000000) };

template <typename TermType>
std::unique_ptr<TermVector>
make_subterms(const std::vector<TermType>& values)
{
    using TermVectorType = std::conditional_t<std::is_same_v<TermType,int64_t>,IntegerTermVector,StringTermVector>;
    auto terms = std::make_unique<TermVectorType>(values.size());
    for (auto term : values) {
        terms->addTerm(term);
    }
    return terms;
}

template <typename TermType>
void
verify_subterms(InTerm& in_term, const std::vector<TermType>& values)
{
    EXPECT_EQ(values.size(), in_term.getNumTerms());
    uint32_t i = 0;
    for (auto term : values) {
        if constexpr (std::is_same_v<TermType, int64_t>) {
            EXPECT_EQ(term, in_term.getAsInteger(i).first);
        } else {
            EXPECT_EQ(term, in_term.getAsString(i).first);
        }
        ++i;
    }
}

template <typename TermType>
void
verify_in_node(Node& node, const std::vector<TermType>& values)
{
    auto in_term = as_node<InTerm>(&node);
    verify_subterms(*in_term, values);
}

template <typename TermType>
void
test_in_node(const std::vector<TermType>& values)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.add_in_term(make_subterms(values), MultiTerm::Type::STRING,
                        "view", 0, Weight(0));
    auto node = builder.build();
    string stack_dump = StackDumpCreator::create(*node);
    SimpleQueryStackDumpIterator iterator(stack_dump);
    verify_in_node(*QueryTreeCreator<SimpleQueryNodeTypes>::create(iterator), values);
    verify_in_node(*QueryTreeCreator<SimpleQueryNodeTypes>::replicate(*node), values);
}

}

TEST(QueryBuilderTest, require_that_in_term_with_strings_can_be_created)
{
    test_in_node(in_strings);
}

TEST(QueryBuilderTest, require_that_in_term_with_integers_can_be_created)
{
    test_in_node(in_integers);
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
