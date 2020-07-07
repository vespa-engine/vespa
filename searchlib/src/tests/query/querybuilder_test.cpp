// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for querybuilder.

#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchlib/query/tree/customtypevisitor.h>
#include <vespa/searchlib/query/tree/point.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/log/log.h>
LOG_SETUP("querybuilder_test");
#include <vespa/searchlib/query/tree/querytreecreator.h>

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
const Point position(100, 100);
const int max_distance = 20;
const uint32_t x_aspect = 0;
const Location location(position, max_distance, x_aspect);

PredicateQueryTerm::UP getPredicateQueryTerm() {
    PredicateQueryTerm::UP pqt(new PredicateQueryTerm);
    pqt->addFeature("key", "value");
    pqt->addRangeFeature("key2", 42, 0xfff);
    return pqt;
}

template <class NodeTypes>
Node::UP createQueryTree() {
    QueryBuilder<NodeTypes> builder;
    builder.addAnd(11);
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
        builder.addPredicateQuery(getPredicateQueryTerm(),
                                  view[3], id[3], weight[3]);
        builder.addDotProduct(3, view[2], id[2], weight[2]);
        {
            builder.addStringTerm(str[3], view[3], id[3], weight[3]);
            builder.addStringTerm(str[4], view[4], id[4], weight[4]);
            builder.addStringTerm(str[5], view[5], id[5], weight[5]);
        }
        builder.addWandTerm(2, view[0], id[0], weight[0], 57, 67, 77.7);
        {
            builder.addStringTerm(str[1], view[1], id[1], weight[1]);
            builder.addStringTerm(str[2], view[2], id[2], weight[2]);
        }
        builder.addRegExpTerm(str[5], view[5], id[5], weight[5]);
        builder.addSameElement(3, view[4]);
        {
            builder.addStringTerm(str[4], view[4], id[4], weight[5]);
            builder.addStringTerm(str[5], view[5], id[5], weight[6]);
            builder.addStringTerm(str[6], view[6], id[6], weight[7]);
        }
        builder.add_nearest_neighbor_term("query_tensor", "doc_tensor", id[3], weight[5], 7, true, 33);
    }
    Node::UP node = builder.build();
    ASSERT_TRUE(node.get());
    return node;
}

template <class TermType>
bool compareTerms(const TermType &expected, const TermType &actual) {
    return EXPECT_TRUE(expected == actual);
}
template <typename T>
bool compareTerms(const std::unique_ptr<T> &expected,
                  const std::unique_ptr<T> &actual) {
    return EXPECT_TRUE(*expected == *actual);
}

template <class Term>
bool checkTerm(const Term *term, const typename Term::Type &t, const string &f,
               int32_t i, Weight w, bool ranked = true,
               bool use_position_data = true) {
    return EXPECT_TRUE(term != 0) &&
        (EXPECT_TRUE(compareTerms(t, term->getTerm())) &
         EXPECT_EQUAL(f, term->getView()) &
         EXPECT_EQUAL(i, term->getId()) &
         EXPECT_EQUAL(w.percent(), term->getWeight().percent()) &
         EXPECT_EQUAL(ranked, term->isRanked()) &
         EXPECT_EQUAL(use_position_data, term->usePositionData()));
}

template <class NodeType>
NodeType*
as_node(Node* node)
{
    auto* result = dynamic_cast<NodeType*>(node);
    ASSERT_TRUE(result != nullptr);
    return result;
}

template <class NodeTypes>
void checkQueryTreeTypes(Node *node) {
    typedef typename NodeTypes::And And;
    typedef typename NodeTypes::AndNot AndNot;
    typedef typename NodeTypes::NumberTerm NumberTerm;
    //typedef typename NodeTypes::NumberTerm FloatTrm;
    typedef typename NodeTypes::Near Near;
    typedef typename NodeTypes::ONear ONear;
    typedef typename NodeTypes::SameElement SameElement;
    typedef typename NodeTypes::Or Or;
    typedef typename NodeTypes::Phrase Phrase;
    typedef typename NodeTypes::PrefixTerm PrefixTerm;
    typedef typename NodeTypes::RangeTerm RangeTerm;
    typedef typename NodeTypes::Rank Rank;
    typedef typename NodeTypes::StringTerm StringTerm;
    //typedef typename NodeTypes::SubstringTerm SubstrTr;
    typedef typename NodeTypes::SuffixTerm SuffixTerm;
    typedef typename NodeTypes::LocationTerm LocationTerm;
    //typedef typename NodeTypes::WeightedSetTerm WeightedSetTerm;
    typedef typename NodeTypes::DotProduct DotProduct;
    typedef typename NodeTypes::WandTerm WandTerm;
    typedef typename NodeTypes::WeakAnd WeakAnd;
    typedef typename NodeTypes::PredicateQuery PredicateQuery;
    typedef typename NodeTypes::RegExpTerm RegExpTerm;

    ASSERT_TRUE(node);
    auto* and_node = as_node<And>(node);
    EXPECT_EQUAL(11u, and_node->getChildren().size());

    auto* rank = as_node<Rank>(and_node->getChildren()[0]);
    EXPECT_EQUAL(2u, rank->getChildren().size());

    auto* near = as_node<Near>(rank->getChildren()[0]);
    EXPECT_EQUAL(2u, near->getChildren().size());
    EXPECT_EQUAL(distance, near->getDistance());
    auto* string_term = as_node<StringTerm>(near->getChildren()[0]);
    EXPECT_TRUE(checkTerm(string_term, str[0], view[0], id[0], weight[0]));
    auto* substring_term = as_node<SubstringTerm>(near->getChildren()[1]);
    EXPECT_TRUE(checkTerm(substring_term, str[1], view[1], id[1], weight[1]));

    auto* onear = as_node<ONear>(rank->getChildren()[1]);
    EXPECT_EQUAL(2u, onear->getChildren().size());
    EXPECT_EQUAL(distance, onear->getDistance());
    auto* suffix_term = as_node<SuffixTerm>(onear->getChildren()[0]);
    EXPECT_TRUE(checkTerm(suffix_term, str[2], view[2], id[2], weight[2]));
    auto* prefix_term = as_node<PrefixTerm>(onear->getChildren()[1]);
    EXPECT_TRUE(checkTerm(prefix_term, str[3], view[3], id[3], weight[3]));

    auto* or_node = as_node<Or>(and_node->getChildren()[1]);
    EXPECT_EQUAL(3u, or_node->getChildren().size());

    auto* phrase = as_node<Phrase>(or_node->getChildren()[0]);
    EXPECT_TRUE(phrase->isRanked());
    EXPECT_EQUAL(weight[4].percent(), phrase->getWeight().percent());
    EXPECT_EQUAL(3u, phrase->getChildren().size());
    string_term = as_node<StringTerm>(phrase->getChildren()[0]);
    EXPECT_TRUE(checkTerm(string_term, str[4], view[4], id[4], weight[4]));
    string_term = as_node<StringTerm>(phrase->getChildren()[1]);
    EXPECT_TRUE(checkTerm(string_term, str[5], view[5], id[5], weight[4]));
    string_term = as_node<StringTerm>(phrase->getChildren()[2]);
    EXPECT_TRUE(checkTerm(string_term, str[6], view[6], id[6], weight[4]));

    phrase = as_node<Phrase>(or_node->getChildren()[1]);
    EXPECT_TRUE(!phrase->isRanked());
    EXPECT_EQUAL(weight[4].percent(), phrase->getWeight().percent());
    EXPECT_EQUAL(2u, phrase->getChildren().size());
    string_term = as_node<StringTerm>(phrase->getChildren()[0]);
    EXPECT_TRUE(checkTerm(string_term, str[4], view[4], id[4], weight[4]));
    string_term = as_node<StringTerm>(phrase->getChildren()[1]);
    EXPECT_TRUE(checkTerm(string_term, str[5], view[5], id[5], weight[4]));

    auto* and_not = as_node<AndNot>(or_node->getChildren()[2]);
    EXPECT_EQUAL(2u, and_not->getChildren().size());
    auto* integer_term = as_node<NumberTerm>(and_not->getChildren()[0]);
    EXPECT_TRUE(checkTerm(integer_term, int1, view[7], id[7], weight[7]));
    auto* float_term = as_node<NumberTerm>(and_not->getChildren()[1]);
    EXPECT_TRUE(checkTerm(float_term, float1, view[8], id[8], weight[8], false));

    auto* range_term = as_node<RangeTerm>(and_node->getChildren()[2]);
    EXPECT_TRUE(checkTerm(range_term, range, view[9], id[9], weight[9]));

    auto* loc_term = as_node<LocationTerm>(and_node->getChildren()[3]);
    EXPECT_TRUE(checkTerm(loc_term, location, view[10], id[10], weight[10]));

    auto* wand = as_node<WeakAnd>(and_node->getChildren()[4]);
    EXPECT_EQUAL(123u, wand->getMinHits());
    EXPECT_EQUAL(2u, wand->getChildren().size());
    string_term = as_node<StringTerm>(wand->getChildren()[0]);
    EXPECT_TRUE(checkTerm(string_term, str[4], view[4], id[4], weight[4]));
    string_term = as_node<StringTerm>(wand->getChildren()[1]);
    EXPECT_TRUE(checkTerm(string_term, str[5], view[5], id[5], weight[5]));

    auto* predicateQuery = as_node<PredicateQuery>(and_node->getChildren()[5]);
    PredicateQueryTerm::UP pqt(new PredicateQueryTerm);
    EXPECT_TRUE(checkTerm(predicateQuery, getPredicateQueryTerm(), view[3], id[3], weight[3]));

    auto* dotProduct = as_node<DotProduct>(and_node->getChildren()[6]);
    EXPECT_EQUAL(3u, dotProduct->getChildren().size());
    string_term = as_node<StringTerm>(dotProduct->getChildren()[0]);
    EXPECT_TRUE(checkTerm(string_term, str[3], view[3], id[3], weight[3]));
    string_term = as_node<StringTerm>(dotProduct->getChildren()[1]);
    EXPECT_TRUE(checkTerm(string_term, str[4], view[4], id[4], weight[4]));
    string_term = as_node<StringTerm>(dotProduct->getChildren()[2]);
    EXPECT_TRUE(checkTerm(string_term, str[5], view[5], id[5], weight[5]));

    auto* wandTerm = as_node<WandTerm>(and_node->getChildren()[7]);
    EXPECT_EQUAL(57u, wandTerm->getTargetNumHits());
    EXPECT_EQUAL(67, wandTerm->getScoreThreshold());
    EXPECT_EQUAL(77.7, wandTerm->getThresholdBoostFactor());
    EXPECT_EQUAL(2u, wandTerm->getChildren().size());
    string_term = as_node<StringTerm>(wandTerm->getChildren()[0]);
    EXPECT_TRUE(checkTerm(string_term, str[1], view[1], id[1], weight[1]));
    string_term = as_node<StringTerm>(wandTerm->getChildren()[1]);
    EXPECT_TRUE(checkTerm(string_term, str[2], view[2], id[2], weight[2]));

    auto* regexp_term = as_node<RegExpTerm>(and_node->getChildren()[8]);
    EXPECT_TRUE(checkTerm(regexp_term, str[5], view[5], id[5], weight[5]));

    auto* same = as_node<SameElement>(and_node->getChildren()[9]);
    EXPECT_EQUAL(view[4], same->getView());
    EXPECT_EQUAL(3u, same->getChildren().size());
    string_term = as_node<StringTerm>(same->getChildren()[0]);
    EXPECT_TRUE(checkTerm(string_term, str[4], view[4], id[4], weight[5]));
    string_term = as_node<StringTerm>(same->getChildren()[1]);
    EXPECT_TRUE(checkTerm(string_term, str[5], view[5], id[5], weight[6]));
    string_term = as_node<StringTerm>(same->getChildren()[2]);
    EXPECT_TRUE(checkTerm(string_term, str[6], view[6], id[6], weight[7]));

    auto* nearest_neighbor = as_node<NearestNeighborTerm>(and_node->getChildren()[10]);
    EXPECT_EQUAL("query_tensor", nearest_neighbor->get_query_tensor_name());
    EXPECT_EQUAL("doc_tensor", nearest_neighbor->getView());
    EXPECT_EQUAL(id[3], nearest_neighbor->getId());
    EXPECT_EQUAL(weight[5].percent(), nearest_neighbor->getWeight().percent());
    EXPECT_EQUAL(7u, nearest_neighbor->get_target_num_hits());
}

struct AbstractTypes {
    typedef search::query::And And;
    typedef search::query::AndNot AndNot;
    typedef search::query::NumberTerm NumberTerm;
    typedef search::query::LocationTerm LocationTerm;
    typedef search::query::Near Near;
    typedef search::query::ONear ONear;
    typedef search::query::SameElement SameElement;
    typedef search::query::Or Or;
    typedef search::query::Phrase Phrase;
    typedef search::query::PrefixTerm PrefixTerm;
    typedef search::query::RangeTerm RangeTerm;
    typedef search::query::Rank Rank;
    typedef search::query::StringTerm StringTerm;
    typedef search::query::SubstringTerm SubstringTerm;
    typedef search::query::SuffixTerm SuffixTerm;
    typedef search::query::WeightedSetTerm WeightedSetTerm;
    typedef search::query::DotProduct DotProduct;
    typedef search::query::WandTerm WandTerm;
    typedef search::query::WeakAnd WeakAnd;
    typedef search::query::PredicateQuery PredicateQuery;
    typedef search::query::RegExpTerm RegExpTerm;
};

// Builds a tree with simplequery and checks that the results have the
// correct abstract types.
TEST("require that Query Trees Can Be Built") {
    Node::UP node = createQueryTree<SimpleQueryNodeTypes>();
    checkQueryTreeTypes<AbstractTypes>(node.get());
}

// Builds a tree with simplequery and checks that the results have the
// correct concrete types.
TEST("require that Simple Query Trees Can Be Built") {
    Node::UP node = createQueryTree<SimpleQueryNodeTypes>();
    checkQueryTreeTypes<SimpleQueryNodeTypes>(node.get());
}

struct MyAnd : And {};
struct MyAndNot : AndNot {};
struct MyEquiv : Equiv {
    MyEquiv(int32_t i, Weight w) : Equiv(i, w) {}
};
struct MyNear : Near { MyNear(size_t dist) : Near(dist) {} };
struct MyONear : ONear { MyONear(size_t dist) : ONear(dist) {} };
struct MyWeakAnd : WeakAnd { MyWeakAnd(uint32_t minHits, const vespalib::string & v) : WeakAnd(minHits, v) {} };
struct MyOr : Or {};
struct MyPhrase : Phrase { MyPhrase(const string &f, int32_t i, Weight w) : Phrase(f, i, w) {}};
struct MySameElement : SameElement { MySameElement(const string &f) : SameElement(f) {}};

struct MyWeightedSetTerm : WeightedSetTerm {
    MyWeightedSetTerm(const string &f, int32_t i, Weight w) : WeightedSetTerm(f, i, w) {}
};
struct MyDotProduct : DotProduct {
    MyDotProduct(const string &f, int32_t i, Weight w) : DotProduct(f, i, w) {}
};
struct MyWandTerm : WandTerm {
    MyWandTerm(const string &f, int32_t i, Weight w, uint32_t targetNumHits,
               int64_t scoreThreshold, double thresholdBoostFactor)
        : WandTerm(f, i, w, targetNumHits, scoreThreshold, thresholdBoostFactor) {}
};
struct MyRank : Rank {};
struct MyNumberTerm : NumberTerm {
    MyNumberTerm(Type t, const string &f, int32_t i, Weight w)
        : NumberTerm(t, f, i, w) {
    }
};
struct MyLocationTerm : LocationTerm {
    MyLocationTerm(const Type &t, const string &f, int32_t i, Weight w)
        : LocationTerm(t, f, i, w) {
    }
};
struct MyPrefixTerm : PrefixTerm {
    MyPrefixTerm(const Type &t, const string &f, int32_t i, Weight w)
        : PrefixTerm(t, f, i, w) {
    }
};
struct MyRangeTerm : RangeTerm {
    MyRangeTerm(const Type &t, const string &f, int32_t i, Weight w)
        : RangeTerm(t, f, i, w) {
    }
};
struct MyStringTerm : StringTerm {
    MyStringTerm(const Type &t, const string &f, int32_t i, Weight w)
        : StringTerm(t, f, i, w) {
    }
};
struct MySubstringTerm : SubstringTerm {
    MySubstringTerm(const Type &t, const string &f, int32_t i, Weight w)
        : SubstringTerm(t, f, i, w) {
    }
};
struct MySuffixTerm : SuffixTerm {
    MySuffixTerm(const Type &t, const string &f, int32_t i, Weight w)
        : SuffixTerm(t, f, i, w) {
    }
};
struct MyPredicateQuery : PredicateQuery {
    MyPredicateQuery(Type &&t, const string &f, int32_t i, Weight w)
        : PredicateQuery(std::move(t), f, i, w) {
    }
};
struct MyRegExpTerm : RegExpTerm {
    MyRegExpTerm(const Type &t, const string &f, int32_t i, Weight w)
        : RegExpTerm(t, f, i, w) {
    }
};
struct MyNearestNeighborTerm : NearestNeighborTerm {
    MyNearestNeighborTerm(vespalib::stringref query_tensor_name, vespalib::stringref field_name,
                          int32_t i, Weight w, uint32_t target_num_hits,
                          bool allow_approximate, uint32_t explore_additional_hits)
        : NearestNeighborTerm(query_tensor_name, field_name, i, w, target_num_hits, allow_approximate, explore_additional_hits)
    {}
};

struct MyQueryNodeTypes {
    typedef MyAnd And;
    typedef MyAndNot AndNot;
    typedef MyEquiv Equiv;
    typedef MyNumberTerm NumberTerm;
    typedef MyLocationTerm LocationTerm;
    typedef MyNear Near;
    typedef MyONear ONear;
    typedef MyOr Or;
    typedef MyPhrase Phrase;
    typedef MySameElement SameElement;
    typedef MyPrefixTerm PrefixTerm;
    typedef MyRangeTerm RangeTerm;
    typedef MyRank Rank;
    typedef MyStringTerm StringTerm;
    typedef MySubstringTerm SubstringTerm;
    typedef MySuffixTerm SuffixTerm;
    typedef MyWeakAnd WeakAnd;
    typedef MyWeightedSetTerm WeightedSetTerm;
    typedef MyDotProduct DotProduct;
    typedef MyWandTerm WandTerm;
    typedef MyPredicateQuery PredicateQuery;
    typedef MyRegExpTerm RegExpTerm;
    typedef MyNearestNeighborTerm NearestNeighborTerm;
};

TEST("require that Custom Query Trees Can Be Built") {
    Node::UP node = createQueryTree<MyQueryNodeTypes>();
    checkQueryTreeTypes<MyQueryNodeTypes>(node.get());
}

TEST("require that Invalid Trees Cannot Be Built") {
    // Incomplete tree.
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addAnd(1);
    ASSERT_TRUE(!builder.build().get());
    EXPECT_EQUAL("QueryBuilderBase::build: QueryBuilder got invalid node structure. _nodes are not empty.", builder.error());

    // Adding a node after build() and before reset() is a no-op.
    builder.addStringTerm(str[0], view[0], id[0], weight[0]);
    ASSERT_TRUE(!builder.build().get());
    EXPECT_EQUAL("QueryBuilderBase::build: QueryBuilder got invalid node structure. _nodes are not empty.", builder.error());

    builder.reset();
    EXPECT_TRUE(builder.error().empty());

    // Too many nodes.
    builder.addAnd(1);
    builder.addStringTerm(str[0], view[0], id[0], weight[0]);
    builder.addStringTerm(str[1], view[1], id[1], weight[1]);
    ASSERT_TRUE(!builder.build().get());
    EXPECT_EQUAL("QueryBuilderBase::addCompleteNode: QueryBuilder got invalid node structure."
                 " Incomming node is 'search::query::SimpleStringTerm', while root is non-null('search::query::SimpleAnd')",
                 builder.error());

    // Adding an intermediate node after build() is also a no-op.
    builder.addAnd(1);
    ASSERT_TRUE(!builder.build().get());
    EXPECT_EQUAL("QueryBuilderBase::addCompleteNode: QueryBuilder got invalid node structure."
                 " Incomming node is 'search::query::SimpleStringTerm', while root is non-null('search::query::SimpleAnd')",
                 builder.error());
}

TEST("require that Rank Can Be Turned Off") {
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

TEST("require that Using Position Data Can Be Turned Off") {
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

TEST("require that Weight Override Works Across Multiple Levels") {
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addPhrase(2, view[0], id[0], weight[0]);

    SimpleStringTerm &string_term_1 = builder.addStringTerm(str[1], view[1], id[1], weight[1]);
    EXPECT_EQUAL(weight[0].percent(), string_term_1.getWeight().percent());

    builder.addAnd(2);
    SimpleStringTerm &string_term_2 = builder.addStringTerm(str[2], view[2], id[2], weight[2]);
    EXPECT_EQUAL(weight[0].percent(), string_term_2.getWeight().percent());
}

TEST("require that Query Tree Creator Can Replicate Queries") {
    Node::UP node = createQueryTree<SimpleQueryNodeTypes>();
    Node::UP new_node = QueryTreeCreator<MyQueryNodeTypes>::replicate(*node);

    checkQueryTreeTypes<SimpleQueryNodeTypes>(node.get());
    checkQueryTreeTypes<MyQueryNodeTypes>(new_node.get());
}

TEST("require that Query Tree Creator Can Create Queries From Stack") {
    Node::UP node = createQueryTree<MyQueryNodeTypes>();
    string stackDump = StackDumpCreator::create(*node);
    SimpleQueryStackDumpIterator iterator(stackDump);

    Node::UP new_node = QueryTreeCreator<SimpleQueryNodeTypes>::create(iterator);
    checkQueryTreeTypes<SimpleQueryNodeTypes>(new_node.get());
}

TEST("require that All Range Syntaxes Work") {

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
    EXPECT_EQUAL(3u, and_node->getChildren().size());

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

TEST("require that empty intermediate node can be added") {
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addAnd(0);
    Node::UP node = builder.build();
    ASSERT_TRUE(node.get());

    string stackDump = StackDumpCreator::create(*node);
    SimpleQueryStackDumpIterator iterator(stackDump);

    Node::UP new_node = QueryTreeCreator<SimpleQueryNodeTypes>::create(iterator);
    And *and_node = dynamic_cast<And *>(new_node.get());
    ASSERT_TRUE(and_node);
    EXPECT_EQUAL(0u, and_node->getChildren().size());
}

TEST("test query parsing error") {
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

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
