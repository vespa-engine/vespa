// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchcore/proton/matching/unpacking_iterators_optimizer.h>
#include <vespa/searchcore/proton/matching/querynodes.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/vespalib/data/smart_buffer.h>
#include <vespa/vespalib/data/output_writer.h>
#include <vespa/vespalib/util/size_literals.h>
#include <string>

using namespace proton::matching;
using namespace search::query;

using vespalib::SmartBuffer;
using vespalib::OutputWriter;

struct DumpQuery : QueryVisitor {
    OutputWriter &out;
    int indent;
    DumpQuery(OutputWriter &out_in, int indent_in)
        : out(out_in), indent(indent_in) {}
    void visit_children(Intermediate &self) {
        DumpQuery sub_dump(out, indent + 2);
        for (Node *node: self.getChildren()) {
            node->accept(sub_dump);
        }
    }
    void visit(And &n) override {
        out.printf("%*s%s %zu\n", indent, "", "And", n.getChildren().size());
        visit_children(n);
    }
    void visit(AndNot &) override {}
    void visit(Equiv &) override {}
    void visit(NumberTerm &) override {}
    void visit(LocationTerm &) override {}
    void visit(Near &) override {}
    void visit(ONear &) override {}
    void visit(Or &n) override {
        out.printf("%*s%s %zu\n", indent, "", "Or", n.getChildren().size());
        visit_children(n);
    }
    void visit(Phrase &n) override {
        out.printf("%*s%s %zu%s\n", indent, "", "Phrase", n.getChildren().size(),
                   n.is_expensive() ? " expensive" : "");
        visit_children(n);
    }
    void visit(SameElement &n) override {
        out.printf("%*s%s %zu%s\n", indent, "", "SameElement", n.getChildren().size(),
                   n.is_expensive() ? " expensive" : "");
        visit_children(n);
    }
    void visit(PrefixTerm &) override {}
    void visit(RangeTerm &) override {}
    void visit(Rank &) override {}
    void visit(StringTerm &n) override {
        out.printf("%*s%s %s%s%s\n", indent, "", "Term", n.getTerm().c_str(),
                   (!n.isRanked() && !n.usePositionData()) ? " cheap" : "",
                   (n.isRanked() != n.usePositionData()) ? " BAD" : "");
    }
    void visit(SubstringTerm &) override {}
    void visit(SuffixTerm &) override {}
    void visit(WeakAnd &) override {}
    void visit(WeightedSetTerm &) override {}
    void visit(DotProduct &) override {}
    void visit(WandTerm &) override {}
    void visit(PredicateQuery &) override {}
    void visit(RegExpTerm &) override {}
    void visit(NearestNeighborTerm &) override {}
};

std::string dump_query(Node &root) {
    SmartBuffer buffer(4_Ki);
    {
        OutputWriter writer(buffer, 1024);
        DumpQuery dumper(writer, 0);
        root.accept(dumper);
    }
    auto mem = buffer.obtain();
    return std::string(mem.data, mem.size);
}

namespace {
std::string view("view");
uint32_t id(5);
Weight weight(7);
}

void add_phrase(QueryBuilder<ProtonNodeTypes> &builder) {
    builder.addPhrase(3, view, id, weight);
    {
        builder.addStringTerm("a", view, id, weight);
        builder.addStringTerm("b", view, id, weight);
        builder.addStringTerm("c", view, id, weight);
    }
}

void add_same_element(QueryBuilder<ProtonNodeTypes> &builder) {
    builder.addSameElement(2, view);
    {
        builder.addStringTerm("x", view, id, weight);
        builder.addStringTerm("y", view, id, weight);
    }
}

Node::UP make_phrase() {
    QueryBuilder<ProtonNodeTypes> builder;
    add_phrase(builder);
    return builder.build();
}

Node::UP make_same_element() {
    QueryBuilder<ProtonNodeTypes> builder;
    add_same_element(builder);
    return builder.build();
}

Node::UP make_query_tree() {
    QueryBuilder<ProtonNodeTypes> builder;
    builder.addAnd(4);
    builder.addOr(3);
    builder.addStringTerm("t2", view, id, weight);
    add_phrase(builder);
    add_same_element(builder);
    add_same_element(builder);
    add_phrase(builder);
    builder.addStringTerm("t1", view, id, weight);
    return builder.build();
}

//-----------------------------------------------------------------------------

std::string plain_phrase_dump =
    "Phrase 3\n"
    "  Term a\n"
    "  Term b\n"
    "  Term c\n";

std::string delayed_phrase_dump =
    "Phrase 3 expensive\n"
    "  Term a\n"
    "  Term b\n"
    "  Term c\n";

std::string split_phrase_dump =
    "And 4\n"
    "  Phrase 3 expensive\n"
    "    Term a\n"
    "    Term b\n"
    "    Term c\n"
    "  Term a cheap\n"
    "  Term b cheap\n"
    "  Term c cheap\n";

//-----------------------------------------------------------------------------

std::string plain_same_element_dump =
    "SameElement 2\n"
    "  Term x\n"
    "  Term y\n";

std::string delayed_same_element_dump =
    "SameElement 2 expensive\n"
    "  Term x\n"
    "  Term y\n";

std::string split_same_element_dump =
    "And 3\n"
    "  SameElement 2 expensive\n"
    "    Term x\n"
    "    Term y\n"
    "  Term x cheap\n"
    "  Term y cheap\n";

//-----------------------------------------------------------------------------

std::string plain_query_tree_dump =
    "And 4\n"
    "  Or 3\n"
    "    Term t2\n"
    "    Phrase 3\n"
    "      Term a\n"
    "      Term b\n"
    "      Term c\n"
    "    SameElement 2\n"
    "      Term x\n"
    "      Term y\n"
    "  SameElement 2\n"
    "    Term x\n"
    "    Term y\n"
    "  Phrase 3\n"
    "    Term a\n"
    "    Term b\n"
    "    Term c\n"
    "  Term t1\n";

std::string delayed_query_tree_dump =
    "And 4\n"
    "  Or 3\n"
    "    Term t2\n"
    "    Phrase 3 expensive\n"
    "      Term a\n"
    "      Term b\n"
    "      Term c\n"
    "    SameElement 2 expensive\n"
    "      Term x\n"
    "      Term y\n"
    "  SameElement 2 expensive\n"
    "    Term x\n"
    "    Term y\n"
    "  Phrase 3 expensive\n"
    "    Term a\n"
    "    Term b\n"
    "    Term c\n"
    "  Term t1\n";

std::string split_query_tree_dump =
    "And 9\n"
    "  Or 3\n"
    "    Term t2\n"
    "    Phrase 3\n"
    "      Term a\n"
    "      Term b\n"
    "      Term c\n"
    "    SameElement 2\n"
    "      Term x\n"
    "      Term y\n"
    "  SameElement 2 expensive\n"
    "    Term x\n"
    "    Term y\n"
    "  Phrase 3 expensive\n"
    "    Term a\n"
    "    Term b\n"
    "    Term c\n"
    "  Term t1\n"
    "  Term x cheap\n"
    "  Term y cheap\n"
    "  Term a cheap\n"
    "  Term b cheap\n"
    "  Term c cheap\n";

std::string delayed_split_query_tree_dump =
    "And 9\n"
    "  Or 3\n"
    "    Term t2\n"
    "    Phrase 3 expensive\n"
    "      Term a\n"
    "      Term b\n"
    "      Term c\n"
    "    SameElement 2 expensive\n"
    "      Term x\n"
    "      Term y\n"
    "  SameElement 2 expensive\n"
    "    Term x\n"
    "    Term y\n"
    "  Phrase 3 expensive\n"
    "    Term a\n"
    "    Term b\n"
    "    Term c\n"
    "  Term t1\n"
    "  Term x cheap\n"
    "  Term y cheap\n"
    "  Term a cheap\n"
    "  Term b cheap\n"
    "  Term c cheap\n";

//-----------------------------------------------------------------------------

Node::UP optimize(Node::UP root, bool white_list, bool split, bool delay) {
    return UnpackingIteratorsOptimizer::optimize(std::move(root), white_list, split, delay);
}

TEST(UnpackingIteratorsOptimizerTest, require_that_root_phrase_node_can_be_left_alone) {
    std::string actual1 = dump_query(*optimize(make_phrase(), false, false, false));
    std::string actual2 = dump_query(*optimize(make_phrase(), false, true, false));
    std::string actual3 = dump_query(*optimize(make_phrase(), true, false, false));
    std::string expect = plain_phrase_dump;
    EXPECT_EQ(actual1, expect);
    EXPECT_EQ(actual2, expect);
    EXPECT_EQ(actual3, expect);
}

TEST(UnpackingIteratorsOptimizerTest, require_that_root_phrase_node_can_be_delayed) {
    std::string actual1 = dump_query(*optimize(make_phrase(), false, false, true));
    std::string actual2 = dump_query(*optimize(make_phrase(), false, true, true));
    std::string actual3 = dump_query(*optimize(make_phrase(), true, false, true));
    std::string expect = delayed_phrase_dump;
    EXPECT_EQ(actual1, expect);
    EXPECT_EQ(actual2, expect);
    EXPECT_EQ(actual3, expect);
}

TEST(UnpackingIteratorsOptimizerTest, require_that_root_phrase_node_can_be_split) {
    std::string actual1 = dump_query(*optimize(make_phrase(), true, true, true));
    std::string actual2 = dump_query(*optimize(make_phrase(), true, true, false));
    std::string expect = split_phrase_dump;
    EXPECT_EQ(actual1, expect);
    EXPECT_EQ(actual2, expect);
}

//-----------------------------------------------------------------------------

TEST(UnpackingIteratorsOptimizerTest, require_that_root_same_element_node_can_be_left_alone) {
    std::string actual1 = dump_query(*optimize(make_same_element(), false, false, false));
    std::string actual2 = dump_query(*optimize(make_same_element(), false, true, false));
    std::string actual3 = dump_query(*optimize(make_same_element(), true, false, false));
    std::string expect = plain_same_element_dump;
    EXPECT_EQ(actual1, expect);
    EXPECT_EQ(actual2, expect);
    EXPECT_EQ(actual3, expect);
}

TEST(UnpackingIteratorsOptimizerTest, require_that_root_same_element_node_can_be_delayed) {
    std::string actual1 = dump_query(*optimize(make_same_element(), false, false, true));
    std::string actual2 = dump_query(*optimize(make_same_element(), false, true, true));
    std::string actual3 = dump_query(*optimize(make_same_element(), true, false, true));
    std::string expect = delayed_same_element_dump;
    EXPECT_EQ(actual1, expect);
    EXPECT_EQ(actual2, expect);
    EXPECT_EQ(actual3, expect);
}

TEST(UnpackingIteratorsOptimizerTest, require_that_root_same_element_node_can_be_split) {
    std::string actual1 = dump_query(*optimize(make_same_element(), true, true, true));
    std::string actual2 = dump_query(*optimize(make_same_element(), true, true, false));
    std::string expect = split_same_element_dump;
    EXPECT_EQ(actual1, expect);
    EXPECT_EQ(actual2, expect);
}

//-----------------------------------------------------------------------------

TEST(UnpackingIteratorsOptimizerTest, require_that_query_tree_can_be_left_alone) {
    std::string actual1 = dump_query(*optimize(make_query_tree(), false, false, false));
    std::string actual2 = dump_query(*optimize(make_query_tree(), true, false, false));
    std::string expect = plain_query_tree_dump;
    EXPECT_EQ(actual1, expect);
    EXPECT_EQ(actual2, expect);
}

TEST(UnpackingIteratorsOptimizerTest, require_that_query_tree_can_be_delayed) {
    std::string actual1 = dump_query(*optimize(make_query_tree(), false, false, true));
    std::string actual2 = dump_query(*optimize(make_query_tree(), true, false, true));
    std::string expect = delayed_query_tree_dump;
    EXPECT_EQ(actual1, expect);
    EXPECT_EQ(actual2, expect);
}

TEST(UnpackingIteratorsOptimizerTest, require_that_query_tree_can_be_split) {
    std::string actual1 = dump_query(*optimize(make_query_tree(), false, true, false));
    std::string actual2 = dump_query(*optimize(make_query_tree(), true, true, false));
    std::string expect = split_query_tree_dump;
    EXPECT_EQ(actual1, expect);
    EXPECT_EQ(actual2, expect);
}

TEST(UnpackingIteratorsOptimizerTest, require_that_query_tree_can_be_delayed_and_split) {
    std::string actual1 = dump_query(*optimize(make_query_tree(), false, true, true));
    std::string actual2 = dump_query(*optimize(make_query_tree(), true, true, true));
    std::string expect = delayed_split_query_tree_dump;
    EXPECT_EQ(actual1, expect);
    EXPECT_EQ(actual2, expect);
}

GTEST_MAIN_RUN_ALL_TESTS()
