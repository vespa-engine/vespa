// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for querybuilder.

#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchlib/query/tree/customtypevisitor.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchlib/query/tree/stackdumpquerycreator.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/equiv_blueprint.h>
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
const string word[N] = {
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
        tv->addTerm(word[i+off], weight[i+off]);
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

struct MyWordAlternatives : WordAlternatives {
    using WordAlternatives::WordAlternatives;
    ~MyWordAlternatives() override;
};


MyWordAlternatives::~MyWordAlternatives() = default;

struct MyQueryNodeTypes : SimpleQueryNodeTypes {
    using WordAlternatives = MyWordAlternatives;
};

struct Expectation {
    bool use_my_node = false;
    static WordAlternatives* as_wa(Node *p) {
        EXPECT_TRUE(p != nullptr);
        auto wap = dynamic_cast<WordAlternatives *>(p);
        EXPECT_TRUE(wap != nullptr);
        return wap;
    }
    void check_wa1(Node *p) {
        if (auto wap = as_wa(p)) {
            EXPECT_EQ(wap->getView(), view[1]);
            const auto& children = wap->getChildren();
            ASSERT_EQ(wap->getNumTerms(), 3);
            ASSERT_EQ(children.size(), 3);
            EXPECT_EQ(children[0]->getTerm(), word[0]);
            EXPECT_EQ(children[1]->getTerm(), word[1]);
            EXPECT_EQ(children[2]->getTerm(), word[2]);
            if (use_my_node) {
                EXPECT_EQ(vespalib::getClassName(*wap), "MyWordAlternatives");
            } else {
                EXPECT_EQ(vespalib::getClassName(*wap), "search::query::SimpleWordAlternatives");
            }
        }
    }
    void check_wa2(Node *p) {
        if (auto wap = as_wa(p)) {
            EXPECT_EQ(wap->getView(), view[2]);
            const auto& children = wap->getChildren();
            ASSERT_EQ(wap->getNumTerms(), 2);
            ASSERT_EQ(children.size(), 2);
            EXPECT_EQ(children[0]->getTerm(), word[3]);
            EXPECT_EQ(children[1]->getTerm(), word[4]);
        }
    }
    void check_wa3(Node *p) {
        if (auto wap = as_wa(p)) {
            EXPECT_EQ(wap->getView(), view[2]);
            const auto& children = wap->getChildren();
            ASSERT_EQ(wap->getNumTerms(), 2);
            ASSERT_EQ(children.size(), 2);
            EXPECT_EQ(children[0]->getTerm(), word[5]);
            EXPECT_EQ(children[1]->getTerm(), word[6]);
        }
    }
    void check_phr(Node *p) {
        EXPECT_TRUE(p != nullptr);
        auto pp = dynamic_cast<Phrase *>(p);
        EXPECT_TRUE(pp != nullptr);
        EXPECT_EQ(pp->getView(), view[2]);
        ASSERT_EQ(pp->getChildren().size(), 2);
        check_wa2(pp->getChildren()[0]);
        check_wa3(pp->getChildren()[1]);
    }
    void check_wa4(Node *p) {
        if (auto wap = as_wa(p)) {
            EXPECT_EQ(wap->getView(), view[3]);
            const auto& children = wap->getChildren();
            ASSERT_EQ(wap->getNumTerms(), 4);
            ASSERT_EQ(children.size(), 4);
            EXPECT_EQ(children[0]->getTerm(), word[7]);
            EXPECT_EQ(children[1]->getTerm(), word[8]);
            EXPECT_EQ(children[2]->getTerm(), word[9]);
            EXPECT_EQ(children[3]->getTerm(), word[10]);
        }
    }
    void check(Node *p) {
        ASSERT_TRUE(p != nullptr);
        auto ap = dynamic_cast<And *>(p);
        ASSERT_TRUE(ap != nullptr);
        ASSERT_EQ(ap->getChildren().size(), 3);
        check_wa1(ap->getChildren()[0]);
        check_phr(ap->getChildren()[1]);
        check_wa4(ap->getChildren()[2]);
    }
};

// Builds a tree with simplequery and checks that the results have the
// correct concrete types.
TEST(WordAlternativesTest, require_that_Simple_Query_Trees_Can_Be_Built) {
    Node::UP node = createQueryTree<SimpleQueryNodeTypes>();
    EXPECT_TRUE(bool(node));
    Expectation expect;
    expect.check(node.get());
}


TEST(WordAlternativesTest, require_that_tree_can_be_replicated) {
    Node::UP node = createQueryTree<SimpleQueryNodeTypes>();
    EXPECT_TRUE(bool(node));
    Node::UP new_node = QueryTreeCreator<MyQueryNodeTypes>::replicate(*node);
    EXPECT_TRUE(bool(new_node));
    Expectation expect;
    expect.use_my_node = true;
    expect.check(new_node.get());
}

TEST(WordAlternativesTest, require_that_tree_can_be_replicated_via_stack) {
    Node::UP node = createQueryTree<SimpleQueryNodeTypes>();
    auto serializedQueryTree = StackDumpCreator::createSerializedQueryTree(*node);
    auto iterator = serializedQueryTree->makeIterator();
    Node::UP new_node = QueryTreeCreator<MyQueryNodeTypes>::create(*iterator);
    EXPECT_TRUE(bool(new_node));
    Expectation expect;
    expect.use_my_node = true;
    expect.check(new_node.get());
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
    fake_index.addResult(view[2], word[3], w1r);
    fake_index.addResult(view[2], word[4], w2r);
    fake_index.addResult(view[2], word[5], w3r);
    fake_index.addResult(view[2], word[6], w4r);
    FakeRequestContext req_ctx;
    FieldSpecList fields;
    fef::MatchDataLayout layout;
    auto handle = layout.allocTermField(42);
    fields.add(FieldSpec(view[2], 42, handle));
    auto bp = fake_index.createBlueprint(req_ctx, fields, *p, layout);
    EXPECT_TRUE(bool(bp));
    // fprintf(stderr, "Got blueprint: '%s'\n", bp->asString().c_str());
    bp->sort(InFlow(true, 1.0));
    EXPECT_TRUE(bp->strict());
    auto md = layout.createMatchData();
    // 7 handles: Phrase[0] + WA[1] + words[2,3] in WA  + WA[4] + words[5,6] in WA
    EXPECT_EQ(7, md->getNumTermFields());
    auto &tfmd = *md->resolveTermField(handle);
    auto s = bp->createSearch(*md);
    EXPECT_TRUE(s->is_strict() == vespalib::Trinary::True);
    s->initFullRange();
    bool ok = s->seek(1);
    EXPECT_FALSE(ok);
    uint32_t docid = s->getDocId();
    EXPECT_EQ(docid, 17);
    s->unpack(docid);
    EXPECT_EQ(tfmd.getFieldId(), 42);
    EXPECT_TRUE(tfmd.has_ranking_data(docid));
    EXPECT_EQ(tfmd.getNumOccs(), 1);
    EXPECT_EQ(tfmd.size(), 1);
    auto iter = tfmd.begin();
    EXPECT_FALSE(iter == tfmd.end());
    {
        const fef::TermFieldMatchDataPosition & pos = *iter;
        EXPECT_EQ(pos.getPosition(), 3);
        EXPECT_DOUBLE_EQ(pos.getMatchExactness(), 0.5);
    }
    ok = s->seek(docid + 1);
    EXPECT_FALSE(ok);
    docid = s->getDocId();
    EXPECT_EQ(docid, 23);
    s->unpack(docid);
    EXPECT_TRUE(tfmd.has_ranking_data(docid));
    EXPECT_EQ(tfmd.getNumOccs(), 1);
    EXPECT_EQ(tfmd.size(), 1);
    iter = tfmd.begin();
    EXPECT_FALSE(iter == tfmd.end());
    {
        const fef::TermFieldMatchDataPosition & pos = *iter;
        EXPECT_EQ(pos.getPosition(), 11);
        EXPECT_FLOAT_EQ(pos.getMatchExactness(), 0.7f);
    }
    ok = s->seek(docid + 1);
    docid = s->getDocId();
    EXPECT_EQ(docid, s->getEndId());
}


GTEST_MAIN_RUN_ALL_TESTS()
