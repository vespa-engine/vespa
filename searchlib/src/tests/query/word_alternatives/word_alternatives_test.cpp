// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for querybuilder.

#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchlib/query/tree/customtypevisitor.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchlib/query/tree/stackdumpquerycreator.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/fake_searchable.h>
#include <vespa/searchlib/queryeval/field_spec.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/classname.h>

#include <vespa/log/log.h>
LOG_SETUP("querybuilder_test");
#include <vespa/searchlib/query/tree/querytreecreator.h>

using string = std::string;
using namespace search;
using namespace search::query;
using namespace search::queryeval;

template <class NodeTypes> void checkQueryTreeTypes(Node *node);

constexpr size_t N = 11;
const string str[N] = {
    "foo", "bar", "baz", "qux", "quux", "corge",
    "grault", "garply", "waldo", "fred", "plugh"
};
const string view[N] = {
    "default",
    "field1", "field2", "field3", "field4", "field5",
    "field6", "field7", "field8", "field9", "field10"
};
const int32_t id[N] = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
const Weight weight[N] = {
    Weight(100),
    Weight(1), Weight(2), Weight(50), Weight(70), Weight(5),
    Weight(6), Weight(7), Weight(80), Weight(90), Weight(10)
};

std::unique_ptr<TermVector> make_tv(size_t sz, size_t off) {
    EXPECT_LE(sz + off, N);
    auto tv = std::make_unique<WeightedStringTermVector>(sz);
    for (size_t i = 0; i < sz; i++) {
        tv->addTerm(str[i+off], weight[i+off]);
    }
    return tv;
}

template <class NodeTypes>
Node::UP createQueryTree() {
    QueryBuilder<NodeTypes> builder;
    builder.addAnd(3);
    builder.add_word_alternatives(make_tv(3, 0), view[1], id[1], weight[1]);
    {
        builder.addPhrase(2, view[2], id[2], weight[0]);
        builder.add_word_alternatives(make_tv(2, 3), view[2], id[0], weight[0]);
        builder.add_word_alternatives(make_tv(2, 5), view[2], id[0], weight[0]);
    }
    builder.add_word_alternatives(make_tv(4, 7), view[3], id[3], weight[3]);
    Node::UP node = builder.build();
    assert(node.get());
    return node;
}


class DumpVisitor : public QueryVisitor
{
public:
    void visit(WordAlternatives&n) override {
        printf("ALTERNATIVES [%d %s]\n", n.getNumTerms(), vespalib::getClassName(n).c_str());
        for (uint32_t i = 0; i < n.getNumTerms(); i++) {
            auto pair = n.getAsString(i);
            printf("- - -> '%s' {weight: %d}\n", pair.first.data(), pair.second.percent());
        }
    }
    void visit(Phrase &n) override {
        const auto & cs = n.getChildren();
        printf("PHRASE [%zd]\n", cs.size());
        for (auto *c : cs) {
            printf("- -> ");
            c->accept(*this);
        }
    }
    void visit(And &n) override {
        const auto & cs = n.getChildren();
        printf("AND [%zd]\n", cs.size());
        for (auto *c : cs) {
            printf("-> ");
            c->accept(*this);
        }
    }
    void visit(AndNot &) override { abort(); }
    void visit(Equiv &) override { abort(); }
    void visit(NumberTerm &) override { abort(); }
    void visit(LocationTerm &) override { abort(); }
    void visit(Near &) override { abort(); }
    void visit(ONear &) override { abort(); }
    void visit(Or &) override { abort(); }
    void visit(SameElement &) override { abort(); }
    void visit(PrefixTerm &) override { abort(); }
    void visit(RangeTerm &) override { abort(); }
    void visit(Rank &) override { abort(); }
    void visit(StringTerm &) override { abort(); }
    void visit(SubstringTerm &) override { abort(); }
    void visit(SuffixTerm &) override { abort(); }
    void visit(WeakAnd &) override { abort(); }
    void visit(WeightedSetTerm &) override { abort(); }
    void visit(DotProduct &) override { abort(); }
    void visit(WandTerm &) override { abort(); }
    void visit(PredicateQuery &) override { abort(); }
    void visit(RegExpTerm &) override { abort(); }
    void visit(NearestNeighborTerm &) override { abort(); }
    void visit(TrueQueryNode &) override { abort(); }
    void visit(FalseQueryNode &) override { abort(); }
    void visit(FuzzyTerm &) override { abort(); }
    void visit(InTerm&) override { abort(); }
};


// Builds a tree with simplequery and checks that the results have the
// correct concrete types.
TEST(WordAlternativesTest, require_that_Simple_Query_Trees_Can_Be_Built) {
    Node::UP node = createQueryTree<SimpleQueryNodeTypes>();
    EXPECT_TRUE(bool(node));
    DumpVisitor dumper;
    node->accept(dumper);
}

struct MyWordAlternatives : WordAlternatives {
    MyWordAlternatives(std::unique_ptr<TermVector> terms, const string& v, int32_t i, Weight w)
      : WordAlternatives(std::move(terms), v, i, w)
    {}
    ~MyWordAlternatives() override;
};


MyWordAlternatives::~MyWordAlternatives() = default;

struct MyQueryNodeTypes : SimpleQueryNodeTypes {
    using WordAlternatives = MyWordAlternatives;
};

TEST(WordAlternativesTest, require_that_tree_can_be_replicated) {
    Node::UP node = createQueryTree<SimpleQueryNodeTypes>();
    EXPECT_TRUE(bool(node));
    Node::UP new_node = QueryTreeCreator<MyQueryNodeTypes>::replicate(*node);
    EXPECT_TRUE(bool(new_node));
    DumpVisitor dumper;
    new_node->accept(dumper);
}

TEST(WordAlternativesTest, require_that_tree_can_be_replicated_via_stack) {
    Node::UP node = createQueryTree<SimpleQueryNodeTypes>();
    string stackDump = StackDumpCreator::create(*node);
    SimpleQueryStackDumpIterator iterator(stackDump);
    Node::UP new_node = QueryTreeCreator<MyQueryNodeTypes>::create(iterator);
    EXPECT_TRUE(bool(new_node));
    DumpVisitor dumper;
    new_node->accept(dumper);
}

TEST(WordAlternativesTest, require_that_blueprints_can_be_built) {
    Node::UP node = createQueryTree<SimpleQueryNodeTypes>();
    auto *a = dynamic_cast<search::query::And *>(node.get());
    EXPECT_TRUE(bool(a));
    auto *p = dynamic_cast<search::query::Phrase *>(a->getChildren()[1]);
    EXPECT_TRUE(bool(p));
    FakeSearchable fake_index;
    auto w1r = FakeResult().doc(7).doc(8).doc(9).doc(10).doc(17).elem(0).len(7).pos(3);
    auto w2r = FakeResult().doc(4).doc(5).doc(6).doc(23).elem(0).len(19).pos(11);
    auto w3r = FakeResult().doc(2).doc(3).doc(23).elem(0).len(19).pos(12);
    auto w4r = FakeResult().doc(17).elem(0).len(7).pos(4);
    fake_index.addResult(view[2], str[3], w1r);
    fake_index.addResult(view[2], str[4], w2r);
    fake_index.addResult(view[2], str[5], w3r);
    fake_index.addResult(view[2], str[6], w4r);
    FakeRequestContext req_ctx;
    FieldSpecList fields;
    fields.add(FieldSpec(view[2], 42, 17));
    auto bp = fake_index.createBlueprint(req_ctx, fields, *p);
    EXPECT_TRUE(bool(bp));
    printf("Got blueprint: '%s'\n", bp->asString().c_str());
}


GTEST_MAIN_RUN_ALL_TESTS()
