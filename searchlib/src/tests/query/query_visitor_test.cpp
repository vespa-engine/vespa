// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for query_visitor.

#include <vespa/searchlib/query/tree/intermediatenodes.h>
#include <vespa/searchlib/query/tree/point.h>
#include <vespa/searchlib/query/tree/queryvisitor.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/string_term_vector.h>
#include <vespa/searchlib/query/tree/termnodes.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("query_visitor_test");

using namespace search::query;

namespace {

class MyVisitor : public QueryVisitor
{
public:
    template <typename T>
    bool &isVisited() {
        static bool b;
        return b;
    }

    void visit(And &) override { isVisited<And>() = true; }
    void visit(AndNot &) override { isVisited<AndNot>() = true; }
    void visit(Equiv &) override { isVisited<Equiv>() = true; }
    void visit(NumberTerm &) override { isVisited<NumberTerm>() = true; }
    void visit(LocationTerm &) override { isVisited<LocationTerm>() = true; }
    void visit(Near &) override { isVisited<Near>() = true; }
    void visit(ONear &) override { isVisited<ONear>() = true; }
    void visit(Or &) override { isVisited<Or>() = true; }
    void visit(Phrase &) override { isVisited<Phrase>() = true; }
    void visit(SameElement &) override { isVisited<SameElement>() = true; }
    void visit(PrefixTerm &) override { isVisited<PrefixTerm>() = true; }
    void visit(RangeTerm &) override { isVisited<RangeTerm>() = true; }
    void visit(Rank &) override { isVisited<Rank>() = true; }
    void visit(StringTerm &) override { isVisited<StringTerm>() = true; }
    void visit(SubstringTerm &) override { isVisited<SubstringTerm>() = true; }
    void visit(SuffixTerm &) override { isVisited<SuffixTerm>() = true; }
    void visit(WeakAnd &) override { isVisited<WeakAnd>() = true; }
    void visit(WeightedSetTerm &) override { isVisited<WeightedSetTerm>() = true; }
    void visit(DotProduct &) override { isVisited<DotProduct>() = true; }
    void visit(WandTerm &) override { isVisited<WandTerm>() = true; }
    void visit(PredicateQuery &) override { isVisited<PredicateQuery>() = true; }
    void visit(RegExpTerm &) override { isVisited<RegExpTerm>() = true; }
    void visit(NearestNeighborTerm &) override { isVisited<NearestNeighborTerm>() = true; }
    void visit(TrueQueryNode &) override { isVisited<TrueQueryNode>() = true; }
    void visit(FalseQueryNode &) override { isVisited<FalseQueryNode>() = true; }
    void visit(FuzzyTerm &) override { isVisited<FuzzyTerm>() = true; }
    void visit(InTerm&) override { isVisited<InTerm>() = true; }
};

template <class T>
void checkVisit(T *node) {
    Node::UP query(node);
    MyVisitor visitor;
    visitor.isVisited<T>() = false;
    query->accept(visitor);
    ASSERT_TRUE(visitor.isVisited<T>());
}

TEST("requireThatAllNodesCanBeVisited") {
    checkVisit<And>(new SimpleAnd);
    checkVisit<AndNot>(new SimpleAndNot);
    checkVisit<Near>(new SimpleNear(0));
    checkVisit<ONear>(new SimpleONear(0));
    checkVisit<Or>(new SimpleOr);
    checkVisit<Phrase>(new SimplePhrase("field", 0, Weight(42)));
    checkVisit<SameElement>(new SimpleSameElement("field", 0, Weight(42)));
    checkVisit<WeightedSetTerm>(new SimpleWeightedSetTerm(0, "field", 0, Weight(42)));
    checkVisit<DotProduct>(new SimpleDotProduct(0, "field", 0, Weight(42)));
    checkVisit<WandTerm>(new SimpleWandTerm(0, "field", 0, Weight(42), 57, 67, 77.7));
    checkVisit<Rank>(new SimpleRank);
    checkVisit<NumberTerm>(new SimpleNumberTerm("0.42", "field", 0, Weight(0)));
    const Location location(Point{10, 10}, 20, 0);
    checkVisit<LocationTerm>(new SimpleLocationTerm(location, "field", 0, Weight(0)));
    checkVisit<PrefixTerm>(new SimplePrefixTerm("t", "field", 0, Weight(0)));
    checkVisit<RangeTerm>(new SimpleRangeTerm(Range(0, 1), "field", 0, Weight(0)));
    checkVisit<StringTerm>(new SimpleStringTerm("t", "field", 0, Weight(0)));
    checkVisit<SubstringTerm>(new SimpleSubstringTerm("t", "field", 0, Weight(0)));
    checkVisit<SuffixTerm>(new SimpleSuffixTerm("t", "field", 0, Weight(0)));
    checkVisit<PredicateQuery>(new SimplePredicateQuery(PredicateQueryTerm::UP(), "field", 0, Weight(0)));
    checkVisit<RegExpTerm>(new SimpleRegExpTerm("t", "field", 0, Weight(0)));
    checkVisit<NearestNeighborTerm>(new SimpleNearestNeighborTerm("query_tensor", "doc_tensor", 0, Weight(0), 123, true, 321, 100100.25));
    checkVisit<TrueQueryNode>(new SimpleTrue());
    checkVisit<FalseQueryNode>(new SimpleFalse());
    checkVisit<FuzzyTerm>(new SimpleFuzzyTerm("t", "field", 0, Weight(0), 2, 0, false));
    checkVisit<InTerm>(new SimpleInTerm(std::make_unique<StringTermVector>(0), MultiTerm::Type::STRING, "field", 0, Weight(0)));
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
