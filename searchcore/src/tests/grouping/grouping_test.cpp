// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/check_require.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcore/grouping/fast_map_lookups.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchcore/grouping/groupingcontext.h>
#include <vespa/searchcore/grouping/groupingmanager.h>
#include <vespa/searchcore/grouping/groupingsession.h>
#include <vespa/searchcore/proton/matching/sessionmanager.h>
#include <vespa/searchlib/aggregation/grouping.h>
#include <vespa/searchlib/aggregation/maxaggregationresult.h>
#include <vespa/searchlib/aggregation/minaggregationresult.h>
#include <vespa/searchlib/aggregation/sumaggregationresult.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/extendableattributes.h>
#include <vespa/searchlib/common/allocatedbitvector.h>
#include <vespa/searchlib/expression/attributenode.h>
#include <vespa/searchlib/expression/decode_fast_map_lookup_node.h>
#include <vespa/searchlib/expression/enumresultnode.h>
#include <vespa/searchlib/expression/integerresultnode.h>
#include <vespa/searchlib/expression/numelemfunctionnode.h>
#include <vespa/searchlib/expression/stringresultnode.h>
#include <vespa/searchlib/test/mock_attribute_context.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/testclock.h>

#include <iostream>
#include <map>

#include <vespa/log/log.h>
LOG_SETUP("grouping_test");

using namespace search::attribute;
using namespace search::aggregation;
using namespace search::expression;
using namespace search::grouping;
using namespace search;
using proton::matching::SessionManager;
using search::attribute::test::MockAttributeContext;
using vespalib::duration;
using vespalib::steady_time;

//-----------------------------------------------------------------------------

const uint32_t NUM_DOCS = 1000;

//-----------------------------------------------------------------------------

struct MyWorld {
    MockAttributeContext       attributeContext;
    document::DocumentType     documentType;
    search::AllocatedBitVector bv;

    MyWorld();
    ~MyWorld();
};

MyWorld::~MyWorld() = default;

MyWorld::MyWorld() : attributeContext(), documentType("test"), bv(NUM_DOCS + 1) {
    bv.setInterval(0, NUM_DOCS);
    // attribute context
    {
        auto                   attr = std::make_shared<SingleInt32ExtAttribute>("attr0");
        AttributeVector::DocId docid;
        for (uint32_t i = 0; i < NUM_DOCS; ++i) {
            attr->addDoc(docid);
            attr->add(i, docid); // value = docid
        }
        CHECK(docid + 1 == NUM_DOCS);
        attributeContext.add(attr);
    }
    {
        auto                   attr = std::make_shared<SingleInt32ExtAttribute>("attr1");
        AttributeVector::DocId docid;
        for (uint32_t i = 0; i < NUM_DOCS; ++i) {
            attr->addDoc(docid);
            attr->add(i * 2, docid); // value = docid * 2
        }
        CHECK(docid + 1 == NUM_DOCS);
        attributeContext.add(attr);
    }
    {
        auto                   attr = std::make_shared<SingleInt32ExtAttribute>("attr2");
        AttributeVector::DocId docid;
        for (uint32_t i = 0; i < NUM_DOCS; ++i) {
            attr->addDoc(docid);
            attr->add(i * 3, docid); // value = docid * 3
        }
        CHECK(docid + 1 == NUM_DOCS);
        attributeContext.add(attr);
    }
    {
        auto                   attr = std::make_shared<SingleInt32ExtAttribute>("attr3");
        AttributeVector::DocId docid;
        for (uint32_t i = 0; i < NUM_DOCS; ++i) {
            attr->addDoc(docid);
            attr->add(i * 4, docid); // value = docid * 4
        }
        CHECK(docid + 1 == NUM_DOCS);
        attributeContext.add(attr);
    }
}

//-----------------------------------------------------------------------------

using GroupingList = GroupingContext::GroupingList;

SessionId createSessionId(const std::string& s) {
    std::vector<char> vec;
    for (char c : s) {
        vec.push_back(c);
    }
    return {vec.data(), vec.size()};
}

class CheckAttributeReferences : public vespalib::ObjectOperation, public vespalib::ObjectPredicate {
public:
    CheckAttributeReferences() : CheckAttributeReferences(false) {}
    explicit CheckAttributeReferences(bool log) : _log(log), _numrefs(0) {}
    bool     _log;
    uint32_t _numrefs;

private:
    void execute(vespalib::Identifiable& obj) override {
        if (_log) {
            std::cerr << _numrefs << ": " << &obj << " = " << obj.asString() << std::endl;
        }
        if (static_cast<AttributeNode&>(obj).getAttribute() != nullptr) {
            _numrefs++;
        }
    }
    bool check(const vespalib::Identifiable& obj) const override { return obj.inherits(AttributeNode::classId); }
};

struct DoomFixture {
    vespalib::TestClock clock;
    steady_time         timeOfDoom;
    DoomFixture() : clock(), timeOfDoom(steady_time::max()) {}
};

//-----------------------------------------------------------------------------

TEST(GroupingTest, testSessionId) {
    SessionId id1;
    ASSERT_TRUE(id1.empty());

    SessionId id2(createSessionId("foo"));
    SessionId id3(createSessionId("bar"));

    ASSERT_TRUE(!id2.empty());
    ASSERT_TRUE(!id3.empty());
    ASSERT_TRUE(id3 < id2);
    EXPECT_EQ(id2, id2);
}

#define MU std::make_unique

GroupingLevel createGL(ExpressionNode::UP expr, ExpressionNode::UP result) {
    GroupingLevel l;
    l.setExpression(std::move(expr));
    l.addResult(SumAggregationResult().setExpression(std::move(result)));
    return l;
}

GroupingLevel createGL(ExpressionNode::UP expr, ExpressionNode::UP resultExpr, ResultNode::UP result) {
    GroupingLevel l;
    l.setExpression(std::move(expr));
    l.addResult(SumAggregationResult().setExpression(std::move(resultExpr)).resultForUnitTest(result.release()));
    return l;
}
GroupingLevel createGL(size_t maxGroups, ExpressionNode::UP expr) {
    GroupingLevel l;
    l.setMaxGroups(maxGroups);
    l.setExpression(std::move(expr));
    return l;
}

TEST(GroupingTest, testGroupingContextInitialization) {
    DoomFixture         f1;
    vespalib::nbostream os;
    Grouping            baseRequest;
    baseRequest.setRoot(Group().addResult(SumAggregationResult().setExpression(MU<AttributeNode>("attr0"))))
        .addLevel(createGL(MU<AttributeNode>("attr1"), MU<AttributeNode>("attr2")))
        .addLevel(createGL(MU<AttributeNode>("attr2"), MU<AttributeNode>("attr3")))
        .addLevel(createGL(MU<AttributeNode>("attr3"), MU<AttributeNode>("attr1")));

    vespalib::NBOSerializer nos(os);
    nos << (uint32_t)1;
    baseRequest.serialize(nos);

    AllocatedBitVector bv(1);
    GroupingContext    context(bv, f1.clock.nowRef(), f1.timeOfDoom, os.data(), os.size());
    ASSERT_TRUE(!context.empty());
    GroupingContext::GroupingList list = context.getGroupingList();
    ASSERT_TRUE(list.size() == 1);
    EXPECT_EQ(list[0]->asString(), baseRequest.asString());
    context.reset();
    ASSERT_TRUE(context.empty());
}

TEST(GroupingTest, testGroupingContextUsage) {
    DoomFixture         f1;
    vespalib::nbostream os;
    Grouping            request1;
    request1.setFirstLevel(0)
        .setLastLevel(0)
        .setRoot(Group().addResult(SumAggregationResult().setExpression(MU<AttributeNode>("attr0"))))
        .addLevel(createGL(MU<AttributeNode>("attr1"), MU<AttributeNode>("attr2")))
        .addLevel(createGL(MU<AttributeNode>("attr2"), MU<AttributeNode>("attr3")))
        .addLevel(createGL(MU<AttributeNode>("attr3"), MU<AttributeNode>("attr1")));

    Grouping request2;
    request2.setFirstLevel(0)
        .setLastLevel(3)
        .setRoot(Group().addResult(SumAggregationResult().setExpression(MU<AttributeNode>("attr0"))))
        .addLevel(createGL(MU<AttributeNode>("attr1"), MU<AttributeNode>("attr2")))
        .addLevel(createGL(MU<AttributeNode>("attr2"), MU<AttributeNode>("attr3")))
        .addLevel(createGL(MU<AttributeNode>("attr3"), MU<AttributeNode>("attr1")));

    auto               r1 = std::make_shared<Grouping>(request1);
    auto               r2 = std::make_shared<Grouping>(request2);
    AllocatedBitVector bv(1);
    GroupingContext    context(bv, f1.clock.nowRef(), f1.timeOfDoom);
    ASSERT_TRUE(context.empty());
    context.addGrouping(r1);
    ASSERT_TRUE(context.getGroupingList().size() == 1);
    context.addGrouping(r2);
    ASSERT_TRUE(context.getGroupingList().size() == 2);
    context.reset();
    ASSERT_TRUE(context.empty());
}

TEST(GroupingTest, testGroupingContextSerializing) {
    DoomFixture f1;
    Grouping    baseRequest;
    baseRequest.setRoot(Group().addResult(SumAggregationResult().setExpression(MU<AttributeNode>("attr0"))))
        .addLevel(createGL(MU<AttributeNode>("attr1"), MU<AttributeNode>("attr2")))
        .addLevel(createGL(MU<AttributeNode>("attr2"), MU<AttributeNode>("attr3")))
        .addLevel(createGL(MU<AttributeNode>("attr3"), MU<AttributeNode>("attr1")));

    vespalib::nbostream     os;
    vespalib::NBOSerializer nos(os);
    nos << (uint32_t)1;
    baseRequest.serialize(nos);

    AllocatedBitVector bv(1);
    GroupingContext    context(bv, f1.clock.nowRef(), f1.timeOfDoom);
    auto               bp = std::make_shared<Grouping>(baseRequest);
    context.addGrouping(bp);
    context.serialize();
    vespalib::nbostream& res(context.getResult());
    EXPECT_EQ(res.size(), os.size());
    ASSERT_TRUE(memcmp(res.data(), os.data(), res.size()) == 0);
}

TEST(GroupingTest, testGroupingManager) {
    DoomFixture         f1;
    vespalib::nbostream os;
    Grouping            request1;
    request1.setFirstLevel(0)
        .setLastLevel(0)
        .setRoot(Group().addResult(SumAggregationResult().setExpression(MU<AttributeNode>("attr0"))))
        .addLevel(createGL(MU<AttributeNode>("attr1"), MU<AttributeNode>("attr2")))
        .addLevel(createGL(MU<AttributeNode>("attr2"), MU<AttributeNode>("attr3")));

    AllocatedBitVector bv(1);
    GroupingContext    context(bv, f1.clock.nowRef(), f1.timeOfDoom);
    auto               bp = std::make_shared<Grouping>(request1);
    context.addGrouping(bp);
    GroupingManager manager(context);
    ASSERT_TRUE(!manager.empty());
}

TEST(GroupingTest, enum_optimization_can_be_disabled_per_context) {
    DoomFixture f1;
    MyWorld     world;
    world.attributeContext.add(
        AttributeFactory::createAttribute("str", Config(BasicType::STRING, CollectionType::SINGLE)));
    AllocatedBitVector bv(1);

    GroupingContext context(bv, f1.clock.nowRef(), f1.timeOfDoom);
    EXPECT_TRUE(context.enum_optimization_enabled());
    context.set_enum_optimization_enabled(false);
    GroupingContext copy(context);
    EXPECT_FALSE(copy.enum_optimization_enabled());

    for (bool enabled : {true, false}) {
        SCOPED_TRACE(enabled ? "enabled" : "disabled");
        GroupingContext ctx(bv, f1.clock.nowRef(), f1.timeOfDoom);
        ctx.set_enum_optimization_enabled(enabled);
        auto request = std::make_shared<Grouping>();
        request->setFirstLevel(0).setLastLevel(0).addLevel(
            createGL(MU<AttributeNode>("str"), MU<AttributeNode>("attr0")));
        ctx.addGrouping(request);
        GroupingManager manager(ctx);
        manager.init(world.attributeContext, nullptr);
        ASSERT_EQ(1u, ctx.getGroupingList().size());
        const ResultNode* result = ctx.getGroupingList()[0]->levels()[0].getExpression().getResult();
        ASSERT_TRUE(result != nullptr);
        EXPECT_EQ(enabled, result->inherits(EnumResultNode::classId));
        EXPECT_EQ(!enabled, result->inherits(StringResultNode::classId));
    }
}

TEST(GroupingTest, testGroupingSession) {
    DoomFixture         f1;
    MyWorld             world;
    vespalib::nbostream os;
    Grouping            request1;
    request1.setId(0)
        .setFirstLevel(0)
        .setLastLevel(0)
        .addLevel(createGL(MU<AttributeNode>("attr1"), MU<AttributeNode>("attr2")))
        .addLevel(createGL(MU<AttributeNode>("attr2"), MU<AttributeNode>("attr3")));

    Grouping request2;
    request2.setId(1)
        .setFirstLevel(0)
        .setLastLevel(3)
        .addLevel(createGL(MU<AttributeNode>("attr1"), MU<AttributeNode>("attr2")))
        .addLevel(createGL(MU<AttributeNode>("attr2"), MU<AttributeNode>("attr3")))
        .addLevel(createGL(MU<AttributeNode>("attr3"), MU<AttributeNode>("attr1")));

    CheckAttributeReferences attrCheck;
    request1.select(attrCheck, attrCheck);
    EXPECT_EQ(0u, attrCheck._numrefs);
    request2.select(attrCheck, attrCheck);
    EXPECT_EQ(0u, attrCheck._numrefs);

    auto            r1 = std::make_shared<Grouping>(request1);
    auto            r2 = std::make_shared<Grouping>(request2);
    GroupingContext initContext(world.bv, f1.clock.nowRef(), f1.timeOfDoom);
    initContext.addGrouping(r1);
    initContext.addGrouping(r2);
    SessionId id("foo");

    // Test initialization phase
    GroupingSession          session(id, initContext, world.attributeContext, &world.documentType);
    CheckAttributeReferences attrCheck2;
    EXPECT_EQ(2u, initContext.getGroupingList().size());
    for (const auto& g : initContext.getGroupingList()) {
        g->select(attrCheck2, attrCheck2);
    }
    EXPECT_EQ(8u, attrCheck2._numrefs);
    RankedHit hit;
    hit._docId = 0;
    GroupingManager& manager(session.getGroupingManager());
    manager.groupInRelevanceOrder(7, {&hit, 1});
    CheckAttributeReferences attrCheck_after;
    GroupingList&            gl3(initContext.getGroupingList());
    for (auto& grouping : gl3) {
        grouping->select(attrCheck_after, attrCheck_after);
    }
    EXPECT_EQ(attrCheck_after._numrefs, 0u);
    {
        EXPECT_EQ(id, session.getSessionId());
        ASSERT_TRUE(!session.getGroupingManager().empty());
        ASSERT_TRUE(!session.finished());
        session.continueExecution(initContext);
        ASSERT_TRUE(!session.finished());
    }
    // Test second pass
    {
        GroupingContext context(world.bv, f1.clock.nowRef(), f1.timeOfDoom);
        auto            r = std::make_shared<Grouping>(request1);
        r->setFirstLevel(1);
        r->setLastLevel(1);
        context.addGrouping(r);

        session.continueExecution(context);
        ASSERT_TRUE(!session.finished());
    }
    // Test last pass. Session should be marked as finished
    {
        GroupingContext context(world.bv, f1.clock.nowRef(), f1.timeOfDoom);
        auto            r = std::make_shared<Grouping>(request1);
        r->setFirstLevel(2);
        r->setLastLevel(2);
        context.addGrouping(r);

        session.continueExecution(context);
        ASSERT_TRUE(session.finished());
    }
}

// A multi-pass session keeps its own copy of the grouping and leaves the request object behind as
// merge target for continueExecution(). Aggregation results at the root level are merged (not
// copied) into that target, so it must have been prepared with a result node of the proper type.
// Corresponds to "all(output(min(attr0)) all(group(attr1) each(output(count()))))".
TEST(GroupingTest, testSessionWithRootLevelAggregationResult) {
    DoomFixture f1;
    MyWorld     world;
    Grouping    request;
    request.setId(0)
        .setFirstLevel(0)
        .setLastLevel(0)
        .setRoot(Group().addResult(MinAggregationResult().setExpression(MU<AttributeNode>("attr0"))))
        .addLevel(createGL(MU<AttributeNode>("attr1"), MU<AttributeNode>("attr2")));

    auto            r = std::make_shared<Grouping>(request);
    GroupingContext initContext(world.bv, f1.clock.nowRef(), f1.timeOfDoom);
    initContext.addGrouping(r);
    // The request has a fallback result node for min() until the session prepares it
    const AggregationResult& rootMin = r->getRoot().getAggregationResult(0);
    EXPECT_TRUE(rootMin.getResult() != nullptr);
    EXPECT_EQ("search::expression::FloatResultNode", std::string(rootMin.getResult()->getClass().name()));

    SessionId       id("foo");
    GroupingSession session(id, initContext, world.attributeContext, &world.documentType);
    ASSERT_TRUE(rootMin.getResult() != nullptr);
    // attr0 is a single int32 attribute, so the result node must be an integer, not the fallback
    EXPECT_EQ("search::expression::Int64ResultNode", std::string(rootMin.getResult()->getClass().name()));

    RankedHit hit;
    hit._docId = 3;
    session.getGroupingManager().groupInRelevanceOrder(7, {&hit, 1});
    session.continueExecution(initContext);
    ASSERT_TRUE(!session.finished());

    EXPECT_EQ(3, static_cast<const MinAggregationResult&>(rootMin).getMin().getInteger()); // attr0 == docid
}

// Same bug as testSessionWithRootLevelAggregationResult, but with two root-level aggregation
// results on the same request; both must independently get their proper (non-fallback) result
// node type, not just the first one visited by select().
TEST(GroupingTest, testSessionWithMultipleRootLevelAggregationResults) {
    DoomFixture f1;
    MyWorld     world;
    Grouping    request;
    request.setId(0)
        .setFirstLevel(0)
        .setLastLevel(0)
        .setRoot(Group()
                     .addResult(MinAggregationResult().setExpression(MU<AttributeNode>("attr0")))
                     .addResult(MaxAggregationResult().setExpression(MU<AttributeNode>("attr1"))))
        .addLevel(createGL(MU<AttributeNode>("attr1"), MU<AttributeNode>("attr2")));

    auto            r = std::make_shared<Grouping>(request);
    GroupingContext initContext(world.bv, f1.clock.nowRef(), f1.timeOfDoom);
    initContext.addGrouping(r);
    const AggregationResult& rootMin = r->getRoot().getAggregationResult(0);
    const AggregationResult& rootMax = r->getRoot().getAggregationResult(1);
    ASSERT_TRUE(rootMin.getResult() != nullptr);
    ASSERT_TRUE(rootMax.getResult() != nullptr);
    EXPECT_EQ("search::expression::FloatResultNode", std::string(rootMin.getResult()->getClass().name()));
    EXPECT_EQ("search::expression::FloatResultNode", std::string(rootMax.getResult()->getClass().name()));

    SessionId       id("foo");
    GroupingSession session(id, initContext, world.attributeContext, &world.documentType);
    // attr0 and attr1 are both single int32 attributes, so both results must be integers
    EXPECT_EQ("search::expression::Int64ResultNode", std::string(rootMin.getResult()->getClass().name()));
    EXPECT_EQ("search::expression::Int64ResultNode", std::string(rootMax.getResult()->getClass().name()));

    RankedHit hit;
    hit._docId = 3;
    session.getGroupingManager().groupInRelevanceOrder(7, {&hit, 1});
    session.continueExecution(initContext);
    ASSERT_TRUE(!session.finished());

    EXPECT_EQ(3, static_cast<const MinAggregationResult&>(rootMin).getMin().getInteger()); // attr0 == docid
    EXPECT_EQ(6, static_cast<const MaxAggregationResult&>(rootMax).getMax().getInteger()); // attr1 == docid*2
}

// Same setup as testSessionWithRootLevelAggregationResult, but continued across all passes of a
// multi-level grouping (mirrors the pass structure of testGroupingSession). The root-level
// aggregation result is only merged into during the first pass (later passes are "frozen" at the
// root level), so it must keep the value and type it got there for the remainder of the session.
TEST(GroupingTest, testSessionWithRootLevelAggregationResultAcrossPasses) {
    DoomFixture f1;
    MyWorld     world;
    Grouping    request;
    request.setId(0)
        .setFirstLevel(0)
        .setLastLevel(0)
        .setRoot(Group().addResult(MinAggregationResult().setExpression(MU<AttributeNode>("attr0"))))
        .addLevel(createGL(MU<AttributeNode>("attr1"), MU<AttributeNode>("attr2")))
        .addLevel(createGL(MU<AttributeNode>("attr2"), MU<AttributeNode>("attr3")));

    auto            r = std::make_shared<Grouping>(request);
    GroupingContext initContext(world.bv, f1.clock.nowRef(), f1.timeOfDoom);
    initContext.addGrouping(r);
    const AggregationResult& rootMin = r->getRoot().getAggregationResult(0);

    SessionId       id("foo");
    GroupingSession session(id, initContext, world.attributeContext, &world.documentType);
    EXPECT_EQ("search::expression::Int64ResultNode", std::string(rootMin.getResult()->getClass().name()));

    RankedHit hit;
    hit._docId = 3;
    session.getGroupingManager().groupInRelevanceOrder(7, {&hit, 1});

    // First pass: root level (0) is merged, since firstLevel == 0 here.
    session.continueExecution(initContext);
    ASSERT_TRUE(!session.finished());
    EXPECT_EQ("search::expression::Int64ResultNode", std::string(rootMin.getResult()->getClass().name()));
    EXPECT_EQ(3, static_cast<const MinAggregationResult&>(rootMin).getMin().getInteger()); // attr0 == docid

    // Second pass: a fresh request object, root level is now frozen (firstLevel == 1) so it is
    // not touched again; the result already delivered in the first pass must remain intact.
    {
        GroupingContext context(world.bv, f1.clock.nowRef(), f1.timeOfDoom);
        auto            r2 = std::make_shared<Grouping>(request);
        r2->setFirstLevel(1);
        r2->setLastLevel(1);
        context.addGrouping(r2);

        session.continueExecution(context);
        ASSERT_TRUE(!session.finished());
    }
    EXPECT_EQ("search::expression::Int64ResultNode", std::string(rootMin.getResult()->getClass().name()));
    EXPECT_EQ(3, static_cast<const MinAggregationResult&>(rootMin).getMin().getInteger());

    // Third (last) pass: session should be marked as finished, first-pass result still intact.
    {
        GroupingContext context(world.bv, f1.clock.nowRef(), f1.timeOfDoom);
        auto            r3 = std::make_shared<Grouping>(request);
        r3->setFirstLevel(2);
        r3->setLastLevel(2);
        context.addGrouping(r3);

        session.continueExecution(context);
        ASSERT_TRUE(session.finished());
    }
    EXPECT_EQ("search::expression::Int64ResultNode", std::string(rootMin.getResult()->getClass().name()));
    EXPECT_EQ(3, static_cast<const MinAggregationResult&>(rootMin).getMin().getInteger());
}

TEST(GroupingTest, testEmptySessionId) {
    DoomFixture         f1;
    MyWorld             world;
    vespalib::nbostream os;
    Grouping            request1;
    request1.setId(0)
        .setFirstLevel(0)
        .setLastLevel(0)
        .addLevel(createGL(MU<AttributeNode>("attr1"), MU<AttributeNode>("attr2")))
        .addLevel(createGL(MU<AttributeNode>("attr2"), MU<AttributeNode>("attr3")));

    auto            r1 = std::make_shared<Grouping>(request1);
    GroupingContext initContext(world.bv, f1.clock.nowRef(), f1.timeOfDoom);
    initContext.addGrouping(r1);
    SessionId id;

    // Test initialization phase
    GroupingSession session(id, initContext, world.attributeContext, &world.documentType);
    RankedHit       hit;
    hit._docId = 0;
    GroupingManager& manager(session.getGroupingManager());
    manager.groupInRelevanceOrder(8, {&hit, 1});
    EXPECT_EQ(id, session.getSessionId());
    ASSERT_TRUE(!session.getGroupingManager().empty());
    ASSERT_TRUE(session.finished() && session.getSessionId().empty());
    session.continueExecution(initContext);
    ASSERT_TRUE(session.finished());
    ASSERT_TRUE(r1->getRoot().getChildrenSize() > 0);
}

TEST(GroupingTest, testSessionManager) {
    DoomFixture         f1;
    MyWorld             world;
    vespalib::nbostream os;
    Grouping            request1;
    request1.setId(0)
        .setFirstLevel(0)
        .setLastLevel(0)
        .addLevel(createGL(MU<AttributeNode>("attr1"), MU<AttributeNode>("attr2"), MU<Int64ResultNode>(0)))
        .addLevel(createGL(MU<AttributeNode>("attr2"), MU<AttributeNode>("attr3"), MU<Int64ResultNode>(0)))
        .setRoot(Group().addResult(
            SumAggregationResult().setExpression(MU<AttributeNode>("attr0")).resultForUnitTest(Int64ResultNode(0))));

    auto            r1 = std::make_shared<Grouping>(request1);
    GroupingContext initContext(world.bv, f1.clock.nowRef(), f1.timeOfDoom);
    initContext.addGrouping(r1);

    SessionManager mgr(2);
    SessionId      id1("foo");
    SessionId      id2("bar");
    SessionId      id3("baz");
    auto s1 = std::make_unique<GroupingSession>(id1, initContext, world.attributeContext, &world.documentType);
    auto s2 = std::make_unique<GroupingSession>(id2, initContext, world.attributeContext, &world.documentType);
    auto s3 = std::make_unique<GroupingSession>(id3, initContext, world.attributeContext, &world.documentType);

    ASSERT_EQ(f1.timeOfDoom, s1->getTimeOfDoom());
    mgr.insert(std::move(s1));
    s1 = mgr.pickGrouping(id1);
    ASSERT_TRUE(s1.get());
    EXPECT_EQ(id1, s1->getSessionId());

    mgr.insert(std::move(s1));
    mgr.insert(std::move(s2));
    mgr.insert(std::move(s3));
    s1 = mgr.pickGrouping(id1);
    s2 = mgr.pickGrouping(id2);
    s3 = mgr.pickGrouping(id3);
    ASSERT_FALSE(s1);
    ASSERT_TRUE(s2);
    ASSERT_TRUE(s3);
    EXPECT_EQ(id2, s2->getSessionId());
    EXPECT_EQ(id3, s3->getSessionId());
    SessionManager::Stats stats = mgr.getGroupingStats();
    EXPECT_EQ(4u, stats.numInsert);
    EXPECT_EQ(3u, stats.numPick);
    EXPECT_EQ(1u, stats.numDropped);
}

void doGrouping(GroupingContext& ctx, uint32_t doc1, double rank1, uint32_t doc2, double rank2, uint32_t doc3,
                double rank3) {
    GroupingManager        man(ctx);
    std::vector<RankedHit> hits;
    hits.emplace_back(doc1, rank1);
    hits.emplace_back(doc2, rank2);
    hits.emplace_back(doc3, rank3);
    man.groupInRelevanceOrder(9, {&hits[0], 3});
}

TEST(GroupingTest, test_grouping_fork_and_join) {
    DoomFixture f1;
    MyWorld     world;

    Grouping request;
    request.setRoot(Group().addResult(SumAggregationResult().setExpression(MU<AttributeNode>("attr0"))))
        .addLevel(createGL(3, MU<AttributeNode>("attr0")))
        .setFirstLevel(0)
        .setLastLevel(1);

    auto            g1 = std::make_shared<Grouping>(request);
    GroupingContext context(world.bv, f1.clock.nowRef(), f1.timeOfDoom);
    context.addGrouping(g1);
    GroupingSession session(SessionId(), context, world.attributeContext, &world.documentType);
    session.prepareThreadContextCreation(4);
    GroupingContext::UP ctx0 = session.createThreadContext(0, world.attributeContext, &world.documentType);
    GroupingContext::UP ctx1 = session.createThreadContext(1, world.attributeContext, &world.documentType);
    GroupingContext::UP ctx2 = session.createThreadContext(2, world.attributeContext, &world.documentType);
    GroupingContext::UP ctx3 = session.createThreadContext(3, world.attributeContext, &world.documentType);
    doGrouping(*ctx0, 12, 30.0, 11, 20.0, 10, 10.0);
    doGrouping(*ctx1, 22, 150.0, 21, 40.0, 20, 25.0);
    doGrouping(*ctx2, 32, 100.0, 31, 15.0, 30, 5.0);
    doGrouping(*ctx3, 42, 4.0, 41, 3.0, 40, 2.0); // not merged (verify independent contexts)
    {
        GroupingManager man(*ctx0);
        man.merge(*ctx1);
        man.merge(*ctx2);
        man.prune();
    }

    Grouping expect;
    expect
        .setRoot(Group()
                     .addResult(SumAggregationResult()
                                    .setExpression(MU<AttributeNode>("attr0"))
                                    .resultForUnitTest(Int64ResultNode(189)))
                     .addChild(Group().setId(Int64ResultNode(21)).setRank(40.0))
                     .addChild(Group().setId(Int64ResultNode(22)).setRank(150.0))
                     .addChild(Group().setId(Int64ResultNode(32)).setRank(100.0)))
        .addLevel(createGL(3, MU<AttributeNode>("attr0")))
        .setFirstLevel(0)
        .setLastLevel(1);

    session.continueExecution(context);
    GroupingContext::GroupingList list = context.getGroupingList();
    ASSERT_TRUE(list.size() == 1);
    EXPECT_EQ(expect.asString(), list[0]->asString());
}

TEST(GroupingTest, fast_map_lookups_are_shared_by_context_copies_and_thread_contexts) {
    DoomFixture f1;
    MyWorld     world;

    auto lookups = std::make_shared<const FastMapLookups>(std::vector<FastMapLookup>{
        {.field_name = "m", .lookup_name = "lookup", .key_field = "key", .key_type = "string",
         .value_field = "value", .value_type = "int"}});
    GroupingContext context(world.bv, f1.clock.nowRef(), f1.timeOfDoom);
    EXPECT_EQ(nullptr, context.get_fast_map_lookups());
    context.set_fast_map_lookups(lookups);
    EXPECT_EQ(lookups.get(), context.get_fast_map_lookups());
    EXPECT_EQ("m$lookup", context.get_fast_map_lookups()->lookups()[0].lookup_attribute());

    GroupingContext copy(context);
    EXPECT_EQ(lookups.get(), copy.get_fast_map_lookups());

    Grouping request;
    request.setRoot(Group().addResult(SumAggregationResult().setExpression(MU<AttributeNode>("attr0"))))
        .addLevel(createGL(3, MU<AttributeNode>("attr0")))
        .setFirstLevel(0)
        .setLastLevel(1);
    context.addGrouping(std::make_shared<Grouping>(request));
    GroupingSession session(SessionId(), context, world.attributeContext, &world.documentType);
    session.prepareThreadContextCreation(2);
    auto ctx0 = session.createThreadContext(0, world.attributeContext, &world.documentType);
    auto ctx1 = session.createThreadContext(1, world.attributeContext, &world.documentType);
    EXPECT_EQ(lookups.get(), ctx0->get_fast_map_lookups());
    EXPECT_EQ(lookups.get(), ctx1->get_fast_map_lookups());
}

namespace {

FastMapLookup make_lookup(const std::string& field_name, const std::string& lookup_name) {
    return {.field_name = field_name,
            .lookup_name = lookup_name,
            .key_field = "key",
            .key_type = "string",
            .value_field = "value",
            .value_type = "int"};
}

struct FastMapWorld {
    MockAttributeContext       attributeContext;
    search::AllocatedBitVector bv;
    std::shared_ptr<const FastMapLookups> lookups;

    static std::string entry(const std::string& key, const std::string& encoded_value) {
        return key + DecodeFastMapLookupNode::encoded_separator + encoded_value;
    }

    FastMapWorld() : attributeContext(), bv(4), lookups() {
        bv.setInterval(0, 3);
        std::vector<std::vector<std::string>> entries{{entry("a", "80000001"), entry("b", "7fffffff")},
                                                      {entry("a", "80000001")},
                                                      {}};
        auto                   map = std::make_shared<MultiStringExtAttribute>("m$lookup");
        auto                   val = std::make_shared<SingleInt32ExtAttribute>("val");
        AttributeVector::DocId docid;
        for (uint32_t i = 0; i < entries.size(); ++i) {
            map->addDoc(docid);
            for (const auto& e : entries[i]) {
                map->add(e.c_str());
            }
            val->addDoc(docid);
            val->add(i + 1, docid); // value = docid + 1
        }
        attributeContext.add(map);
        attributeContext.add(val);
        lookups = std::make_shared<const FastMapLookups>(std::vector<FastMapLookup>{make_lookup("m", "lookup")});
    }
    ~FastMapWorld();

    static Grouping make_request() { return make_request(MU<DecodeFastMapLookupNode>("m.lookup")); }

    static Grouping make_request(ExpressionNode::UP level_expression) {
        Grouping request;
        request.setFirstLevel(0)
            .setLastLevel(1)
            .addLevel(createGL(std::move(level_expression), MU<AttributeNode>("val")));
        return request;
    }

    // Returns the sum of 'val' per decoded entry, after grouping all documents.
    static std::map<std::string, int64_t> group(GroupingContext& context) {
        std::vector<RankedHit> hits{{0, 30.0}, {1, 20.0}, {2, 10.0}};
        GroupingManager        man(context);
        man.groupInRelevanceOrder(0, {hits.data(), hits.size()});
        std::map<std::string, int64_t> result;
        for (const auto& grouping : context.getGroupingList()) {
            const Group& root = grouping->getRoot();
            for (uint32_t i = 0; i < root.getChildrenSize(); ++i) {
                const Group& group = *root.groups()[i];
                char         buf[100];
                auto         id = group.getId().getString(vespalib::BufferRef(buf, sizeof(buf)));
                auto&        sum = static_cast<const SumAggregationResult&>(group.getAggregationResult(0));
                result[std::string(id.c_str(), id.size())] = sum.getSum().getInteger();
            }
        }
        return result;
    }
};

FastMapWorld::~FastMapWorld() = default;

} // namespace

TEST(GroupingTest, decode_fast_map_lookup_nodes_are_configured_from_fast_map_lookups) {
    DoomFixture     f1;
    FastMapWorld    world;
    GroupingContext context(world.bv, f1.clock.nowRef(), f1.timeOfDoom);
    context.set_fast_map_lookups(world.lookups);
    context.addGrouping(std::make_shared<Grouping>(FastMapWorld::make_request()));
    GroupingManager manager(context);
    manager.init(world.attributeContext, nullptr);
    ASSERT_EQ(1u, context.getGroupingList().size());
    std::map<std::string, int64_t> expected{{"a:1", 3}, {"b:-1", 1}};
    EXPECT_EQ(expected, FastMapWorld::group(context));
}

TEST(GroupingTest, decode_fast_map_lookup_nodes_are_configured_in_thread_contexts) {
    DoomFixture     f1;
    FastMapWorld    world;
    GroupingContext context(world.bv, f1.clock.nowRef(), f1.timeOfDoom);
    context.set_fast_map_lookups(world.lookups);
    context.addGrouping(std::make_shared<Grouping>(FastMapWorld::make_request()));
    GroupingSession session(SessionId(), context, world.attributeContext, nullptr);
    session.prepareThreadContextCreation(2);
    // The second thread context is deserialized, which drops the value type, and must be configured again.
    auto ctx1 = session.createThreadContext(1, world.attributeContext, nullptr);
    ASSERT_EQ(1u, ctx1->getGroupingList().size());
    std::map<std::string, int64_t> expected{{"a:1", 3}, {"b:-1", 1}};
    EXPECT_EQ(expected, FastMapWorld::group(*ctx1));
}

TEST(GroupingTest, decode_fast_map_lookup_nodes_nested_in_functions_are_configured_and_wired) {
    DoomFixture     f1;
    FastMapWorld    world;
    GroupingContext context(world.bv, f1.clock.nowRef(), f1.timeOfDoom);
    context.set_fast_map_lookups(world.lookups);
    // numelem(decodefastmaplookup(m.lookup)): the decode node and its input are reached through the function node
    context.addGrouping(std::make_shared<Grouping>(
        FastMapWorld::make_request(MU<NumElemFunctionNode>(MU<DecodeFastMapLookupNode>("m.lookup")))));
    GroupingManager manager(context);
    manager.init(world.attributeContext, nullptr);
    ASSERT_EQ(1u, context.getGroupingList().size());
    std::map<std::string, int64_t> expected{{"0", 3}, {"1", 2}, {"2", 1}};
    EXPECT_EQ(expected, FastMapWorld::group(context));
}

TEST(GroupingTest, grouping_with_unknown_fast_map_lookup_is_ignored) {
    DoomFixture  f1;
    FastMapWorld world;
    for (bool with_lookups : {false, true}) {
        SCOPED_TRACE(with_lookups ? "other lookups" : "no lookups");
        GroupingContext context(world.bv, f1.clock.nowRef(), f1.timeOfDoom);
        if (with_lookups) {
            context.set_fast_map_lookups(
                std::make_shared<const FastMapLookups>(std::vector<FastMapLookup>{make_lookup("m", "other")}));
        }
        context.addGrouping(std::make_shared<Grouping>(FastMapWorld::make_request()));
        GroupingManager manager(context);
        manager.init(world.attributeContext, nullptr);
        EXPECT_TRUE(context.getGroupingList().empty());
    }
}

TEST(GroupingTest, fast_map_lookups_are_found_by_lookup_attribute) {
    FastMapLookups lookups(std::vector<FastMapLookup>{make_lookup("m", "lookup"), make_lookup("m.x", "y")});
    ASSERT_NE(nullptr, lookups.find("m$lookup"));
    EXPECT_EQ("lookup", lookups.find("m$lookup")->lookup_name);
    ASSERT_NE(nullptr, lookups.find("m.x$y"));
    EXPECT_EQ("m.x", lookups.find("m.x$y")->field_name);
    EXPECT_EQ(nullptr, lookups.find("m$lookups"));
    EXPECT_EQ(nullptr, lookups.find("mm$lookup"));
    EXPECT_EQ(nullptr, lookups.find("m.lookup"));
    EXPECT_EQ(nullptr, lookups.find("m"));
}

TEST(GroupingTest, test_session_timeout) {
    DoomFixture    f1;
    MyWorld        world;
    SessionManager mgr(2);
    SessionId      id1("foo");
    SessionId      id2("bar");

    GroupingContext initContext1(world.bv, f1.clock.nowRef(), steady_time(duration(10)));
    GroupingContext initContext2(world.bv, f1.clock.nowRef(), steady_time(duration(20)));
    auto s1 = std::make_unique<GroupingSession>(id1, initContext1, world.attributeContext, &world.documentType);
    auto s2 = std::make_unique<GroupingSession>(id2, initContext2, world.attributeContext, &world.documentType);
    mgr.insert(std::move(s1));
    mgr.insert(std::move(s2));
    mgr.pruneTimedOutSessions(steady_time(5ns));
    ASSERT_EQ(2u, mgr.getGroupingStats().numCached);
    mgr.pruneTimedOutSessions(steady_time(10ns));
    ASSERT_EQ(2u, mgr.getGroupingStats().numCached);

    mgr.pruneTimedOutSessions(steady_time(11ns));
    ASSERT_EQ(1u, mgr.getGroupingStats().numCached);

    mgr.pruneTimedOutSessions(steady_time(21ns));
    ASSERT_EQ(0u, mgr.getGroupingStats().numCached);
}

GTEST_MAIN_RUN_ALL_TESTS()
