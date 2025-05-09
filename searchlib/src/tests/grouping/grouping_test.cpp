// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/aggregation/perdocexpression.h>
#include <vespa/searchlib/aggregation/aggregation.h>
#include <vespa/searchlib/attribute/extendableattributes.h>
#include <vespa/searchlib/attribute/attributemanager.h>
#include <vespa/searchlib/aggregation/hitsaggregationresult.h>
#include <vespa/searchlib/aggregation/fs4hit.h>
#include <vespa/searchlib/aggregation/predicates.h>
#include <vespa/searchlib/aggregation/modifiers.h>
#include <vespa/searchlib/expression/documentfieldnode.h>
#include <vespa/searchlib/expression/fixedwidthbucketfunctionnode.h>
#include <vespa/searchlib/test/make_attribute_map_lookup_node.h>
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/exceptions.h>
#include <algorithm>
#include <cmath>
#include <iostream>

#include <vespa/log/log.h>
LOG_SETUP("grouping_test");

using namespace vespalib;
using namespace search;
using namespace search::aggregation;
using namespace search::attribute;
using namespace search::expression;
using search::expression::test::makeAttributeMapLookupNode;

namespace {

const int64_t undefinedInteger = getUndefined<int64_t>();

//-----------------------------------------------------------------------------

template<typename A, typename T>
class AttrBuilder
{
private:
    A *_attr;
    AttributeVector::SP _attrSP;

public:
    AttrBuilder(const AttrBuilder &rhs)
        : _attr(new A(rhs._attr->getName())),
          _attrSP(_attr)
    {
        uint32_t numDocs = rhs._attr->getNumDocs();
        for (uint32_t docid = 0; docid < numDocs; ++docid) {
            T val;
            uint32_t res = rhs._attr->get(docid, &val, 1);
            assert(res == 1);
            add(val);
        }
    }
    explicit AttrBuilder(const std::string &name)
        : _attr(new A(name)),
          _attrSP(_attr)
    {
    }
    AttrBuilder& operator=(const AttrBuilder &rhs) {
        AttrBuilder tmp(rhs);
        std::swap(_attr, tmp._attr);
        _attrSP.swap(tmp._attrSP);
        return *this;
    }
    AttrBuilder &add(T value) {
        DocId ignore;
        _attr->addDoc(ignore);
        _attr->add(value);
        return *this;
    }
    AttrBuilder &add(std::vector<T> values) {
        DocId ignore;
        _attr->addDoc(ignore);
        for (T value : values) {
            _attr->add(value);
        }
        return *this;
    }
    AttributeVector::SP sp() const {
        return _attrSP;
    }
};

using IntAttrBuilder = AttrBuilder<SingleIntegerExtAttribute, int64_t>;
using FloatAttrBuilder = AttrBuilder<SingleFloatExtAttribute, double>;
using StringAttrBuilder = AttrBuilder<SingleStringExtAttribute, const char *>;

using StringArrayAttrBuilder = AttrBuilder<MultiStringExtAttribute, const char *>;
using IntArrayAttrBuilder = AttrBuilder<MultiIntegerExtAttribute, int64_t>;

//-----------------------------------------------------------------------------

class ResultBuilder
{
private:
    std::vector<RankedHit> _hits;

public:
    ResultBuilder() : _hits() {}
    ResultBuilder &add(unsigned int docid, HitRank rank = 0) {
        RankedHit hit;
        hit._docId = docid;
        hit._rankValue = rank;
        _hits.push_back(hit);
        for (uint32_t pos = (_hits.size() - 1);
             pos > 0 && (_hits[pos].getRank() > _hits[pos - 1].getRank());
             --pos)
        {
            std::swap(_hits[pos], _hits[pos - 1]);
        }
        return *this;
    }
    const RankedHit *hits() const {
        return &_hits[0];
    }
    uint32_t size() const {
        return _hits.size();
    }
};

//-----------------------------------------------------------------------------

class AggregationContext
{
private:
    AttributeManager _attrMan;
    ResultBuilder    _result;
    IAttributeContext::UP _attrCtx;

public:
    AggregationContext();
    AggregationContext(const AggregationContext &) = delete;
    AggregationContext &operator=(const AggregationContext &) = delete;
    ~AggregationContext();
    ResultBuilder &result() { return _result; }
    void add(const AttributeVector::SP & attr) {
        _attrMan.add(attr);
    }
    void setup(Grouping &g) {
        g.configureStaticStuff(ConfigureStaticParams(_attrCtx.get(), nullptr));
    }
    const IAttributeContext & attrCtx() const { return *_attrCtx; }
};

AggregationContext::AggregationContext() : _attrMan(), _result(), _attrCtx(_attrMan.createContext()) {}
AggregationContext::~AggregationContext()  = default;

#define MU std::make_unique

class CheckAttributeReferences : public vespalib::ObjectOperation, public vespalib::ObjectPredicate
{
public:
    CheckAttributeReferences() : _numrefs(0) { }
    int _numrefs;
private:
    void execute(vespalib::Identifiable &obj) override {
        if (static_cast<AttributeNode &>(obj).getAttribute() != nullptr) {
            _numrefs++;
        }
    }
    bool check(const vespalib::Identifiable &obj) const override { return obj.inherits(AttributeNode::classId); }
};

/**
 * Run the given grouping request and verify that the resulting group
 * tree matches the expected value.
 **/
bool
testAggregation(AggregationContext &ctx, const Grouping &request, const Group &expect)
{
    Grouping tmp = request; // create local copy
    ctx.setup(tmp);
    tmp.aggregate(ctx.result().hits(), ctx.result().size());
    tmp.cleanupAttributeReferences();
    CheckAttributeReferences attrCheck;
    tmp.select(attrCheck, attrCheck);
    EXPECT_EQ(attrCheck._numrefs, 0);
    bool ok = true;
    EXPECT_EQ(tmp.getRoot().asString(), expect.asString()) << (ok = false, "");
    return ok;
}

std::unique_ptr<AggregationResult>
prepareAggr(const AggregationResult & aggr, ExpressionNode::UP expr) {
    std::unique_ptr<AggregationResult> clone(aggr.clone());
    clone->setExpression(std::move(expr));
    return clone;
}

std::unique_ptr<ExpressionNode>
prepareAggr(const AggregationResult & aggr, ExpressionNode::UP expr, const ResultNode & r) {
    auto prepared = prepareAggr(aggr, std::move(expr));
    prepared->setResult(r);
    return prepared;
}

void
testAggregationSimpleSum(AggregationContext & ctx, const AggregationResult & aggr, const ResultNode & ir,
                         const ResultNode & fr, const ResultNode & sr, const std::string& label)
{
    SCOPED_TRACE(label);
    ExpressionNode::CP clone(aggr);
    Grouping request;
    request.setRoot(Group().addResult(prepareAggr(aggr, MU<AttributeNode>("int")))
                            .addResult(prepareAggr(aggr, MU<AttributeNode>("float")))
                            .addResult(prepareAggr(aggr, MU<AttributeNode>("string"))));

    Group expect;
    expect.addResult(prepareAggr(aggr, MU<AttributeNode>("int"), ir))
            .addResult(prepareAggr(aggr, MU<AttributeNode>("float"), fr))
            .addResult(prepareAggr(aggr, MU<AttributeNode>("string"), sr));

    EXPECT_TRUE(testAggregation(ctx, request, expect));
}

//-----------------------------------------------------------------------------

/**
 * Merge the given grouping requests and verify that the resulting
 * group tree matches the expected value.
 **/
bool
testMerge(const Grouping &a, const Grouping &b, const Group &expect)
{
    Grouping tmp = a; // create local copy
    Grouping tmpB = b;
    tmp.merge(tmpB);
    tmp.postMerge();
    tmp.sortById();
    bool retval = true;
    EXPECT_EQ(tmp.getRoot().asString(), expect.asString()) << (retval = false, "");
    return retval;
}

/**
 * Prune the given grouping request and verify that the resulting
 * group tree matches the expected value.
 **/
bool
testPrune(const Grouping &a, const Grouping &b, const Group &expect)
{
    Grouping tmp = a; // create local copy
    tmp.prune(b);
    bool ok = true;
    EXPECT_EQ(tmp.getRoot().asString(), expect.asString()) << (ok = false, "");
    return ok;
}

/**
 * Merge a given grouping request to get a partial request back. Verify that the
 * partial request is correct.
 **/
bool
testPartialMerge(const Grouping &a, const Grouping &b, const Group &expect)
{
    Grouping tmp = a; // create local copy
    tmp.mergePartial(b);
    bool ok = true;
    EXPECT_EQ(tmp.getRoot().asString(), expect.asString()) << (ok = false, "");
    return ok;
}

/**
 * Merge the given grouping requests and verify that the resulting
 * group tree matches the expected value.
 **/
bool
testMerge(const Grouping &a, const Grouping &b, const Grouping &c, const Group &expect) {
    Grouping tmp = a; // create local copy
    Grouping tmpB = b; // create local copy
    Grouping tmpC = c; // create local copy
    tmp.merge(tmpB);
    tmp.merge(tmpC);
    tmp.postMerge();
    tmp.sortById();
    bool ok = true;
    EXPECT_EQ(tmp.getRoot().asString(), expect.asString()) << (ok = false, "");
    return ok;
}

void
testAggregationSimple(AggregationContext & ctx, const AggregationResult & aggr, const ResultNode & ir, const std::string &name)
{
    ExpressionNode::CP clone(aggr);
    Grouping request;
    request.setRoot(Group().addResult(prepareAggr(aggr, makeAttributeMapLookupNode(name))));

    Group expect;
    expect.addResult(prepareAggr(aggr, makeAttributeMapLookupNode(name), ir));
    EXPECT_TRUE(testAggregation(ctx, request, expect));
}

GroupingLevel
createGL(ExpressionNode::UP expr, ExpressionNode::UP resultExpr) {
    GroupingLevel l;
    l.setExpression(std::move(expr));
    l.addResult(SumAggregationResult().setExpression(std::move(resultExpr)));
    return l;
}

GroupingLevel
createGL(ExpressionNode::UP expr) {
    GroupingLevel l;
    l.setExpression(std::move(expr));
    return l;
}

GroupingLevel
createGL(size_t maxGroups, ExpressionNode::UP expr) {
    GroupingLevel l;
    l.setMaxGroups(maxGroups);
    l.setExpression(std::move(expr));
    return l;
}

GroupingLevel
createGL(size_t maxGroups, ExpressionNode::UP expr, ExpressionNode::UP result) {
    GroupingLevel l;
    l.setMaxGroups(maxGroups);
    l.setExpression(std::move(expr));
    l.addResult(SumAggregationResult().setExpression(std::move(result)));
    return l;
}

template<typename T>
ExpressionNode::UP
createAggr(ExpressionNode::UP e) {
    std::unique_ptr<T> aggr = MU<T>();
    aggr->setExpression(std::move(e));
    return aggr;
}

template<typename T>
ExpressionNode::UP
createAggr(SingleResultNode::UP r, ExpressionNode::UP e) {
    std::unique_ptr<T> aggr = MU<T>(std::move(r));
    aggr->setExpression(std::move(e));
    return aggr;
}

template<typename T>
ExpressionNode::UP
createNumAggr(NumericResultNode::UP r, ExpressionNode::UP e) {
    std::unique_ptr<T> aggr = MU<T>(std::move(r));
    aggr->setExpression(std::move(e));
    return aggr;
}

bool
checkHits(const Grouping &g, uint32_t first, uint32_t last, uint32_t cnt)
{
    CountFS4Hits pop;
    Grouping tmp = g;
    tmp.setFirstLevel(first).setLastLevel(last).select(pop, pop);
    bool ok = true;
    EXPECT_EQ(pop.getHitCount(), cnt) << (ok = false, "");
    return ok;
}

bool
checkBucket(const NumericResultNode &width, const NumericResultNode &value, const BucketResultNode &bucket)
{
    AggregationContext ctx;
    ctx.result().add(0);
    if (value.getClass().inherits(IntegerResultNode::classId)) {
        ctx.add(IntAttrBuilder("attr").add(value.getInteger()).sp());
    }  else if (value.getClass().inherits(FloatResultNode::classId)) {
        ctx.add(FloatAttrBuilder("attr").add(value.getFloat()).sp());
    } else {
        ADD_FAILURE() << "value not inherited from integer or float result node, is " << value.getClass().name();
        return false;
    }
    std::unique_ptr<FixedWidthBucketFunctionNode> fixed = MU<FixedWidthBucketFunctionNode>(MU<AttributeNode>("attr"));
    fixed->setWidth(width);
    Grouping request = Grouping().addLevel(createGL(std::move(fixed)));
    Group expect = Group().addChild(Group().setId(bucket));
    return testAggregation(ctx, request, expect);
}
}

TEST(GroupingTest, Control_size_of_objects) {
    EXPECT_EQ(64u, sizeof(Group));
    EXPECT_EQ(40u, sizeof(Group::Value));
}

/**
 * Test collecting the sum of the values from a single attribute
 * vector directly into the root node. Consider this a smoke test.
 **/
TEST(GroupingTest, testAggregationSimple)
{
    AggregationContext ctx;
    ctx.result().add(0).add(1).add(2);
    ctx.add(IntAttrBuilder("int").add(3).add(7).add(15).sp());
    ctx.add(FloatAttrBuilder("float").add(3).add(7).add(15).sp());
    ctx.add(StringAttrBuilder("string").add("3").add("7").add("15").sp());

    testAggregationSimpleSum(ctx, SumAggregationResult(), Int64ResultNode(25), FloatResultNode(25), StringResultNode("25"), "sum");
    testAggregationSimpleSum(ctx, MinAggregationResult(), Int64ResultNode(3), FloatResultNode(3), StringResultNode("15"), "min");
    testAggregationSimpleSum(ctx, MaxAggregationResult(), Int64ResultNode(15), FloatResultNode(15), StringResultNode("7"), "max");
}

/**
 * Verify that the backend aggregation will classify and collect on
 * the appropriate levels, as indicated by the firstLevel and
 * lastLevel parameters.
 **/
TEST(GroupingTest, testAggregationLevels)
{
    AggregationContext ctx;
    ctx.add(IntAttrBuilder("attr0").add(10).add(10).sp());
    ctx.add(IntAttrBuilder("attr1").add(11).add(11).sp());
    ctx.add(IntAttrBuilder("attr2").add(12).add(12).sp());
    ctx.add(IntAttrBuilder("attr3").add(13).add(13).sp());
    ctx.result().add(0).add(1);

    Grouping baseRequest;
    baseRequest
            .setRoot(Group().addResult(SumAggregationResult().setExpression(MU<AttributeNode>("attr0"))))
            .addLevel(createGL(MU<AttributeNode>("attr1"), MU<AttributeNode>("attr2")))
            .addLevel(createGL(MU<AttributeNode>("attr2"), MU<AttributeNode>("attr3")))
            .addLevel(createGL(MU<AttributeNode>("attr3"), MU<AttributeNode>("attr1")));

    Group notDone;
    notDone.addResult(SumAggregationResult().setExpression(MU<AttributeNode>("attr0")));
// Hmm, do not need to prepare more than the levels needed.    .setResult(Int64ResultNode(0)));

    Group done0;
    done0.addResult(SumAggregationResult().setExpression(MU<AttributeNode>("attr0")).setResult(Int64ResultNode(20)))
         .addChild(Group().setId(Int64ResultNode(11)).addResult(SumAggregationResult().setExpression(MU<AttributeNode>("attr2"))
                                       .setResult(Int64ResultNode(0))));

    Group done1;
    done1.addResult(SumAggregationResult()
                            .setExpression(MU<AttributeNode>("attr0"))
                            .setResult(Int64ResultNode(20)))
         .addChild(Group().setId(Int64ResultNode(11))
                          .addResult(SumAggregationResult()
                                             .setExpression(MU<AttributeNode>("attr2"))
                                             .setResult(Int64ResultNode(24)))
                          .addChild(Group().setId(Int64ResultNode(12))
                                           .addResult(SumAggregationResult()
                                                              .setExpression(MU<AttributeNode>("attr3"))
                                                              .setResult(Int64ResultNode(0)))));

    Group done2;
    done2.addResult(SumAggregationResult()
                            .setExpression(MU<AttributeNode>("attr0"))
                            .setResult(Int64ResultNode(20)))
         .addChild(Group().setId(Int64ResultNode(11))
                          .addResult(SumAggregationResult()
                                             .setExpression(MU<AttributeNode>("attr2"))
                                             .setResult(Int64ResultNode(24)))
                          .addChild(Group().setId(Int64ResultNode(12))
                                           .addResult(SumAggregationResult()
                                                              .setExpression(MU<AttributeNode>("attr3"))
                                                              .setResult(Int64ResultNode(26)))
                                           .addChild(Group().setId(Int64ResultNode(13))
                                                            .addResult(SumAggregationResult()
                                                                               .setExpression(MU<AttributeNode>("attr1"))
                                                                               .setResult(Int64ResultNode(0))))));

    Group done3;
    done3.addResult(SumAggregationResult()
                            .setExpression(MU<AttributeNode>("attr0"))
                            .setResult(Int64ResultNode(20)))
                  .addChild(Group()
                            .setId(Int64ResultNode(11))
                            .addResult(SumAggregationResult()
                                       .setExpression(MU<AttributeNode>("attr2"))
                                       .setResult(Int64ResultNode(24)))
                            .addChild(Group()
                                      .setId(Int64ResultNode(12))
                                      .addResult(SumAggregationResult()
                                              .setExpression(MU<AttributeNode>("attr3"))
                                              .setResult(Int64ResultNode(26)))
                                      .addChild(Group()
                                              .setId(Int64ResultNode(13))
                                              .addResult(SumAggregationResult()
                                                      .setExpression(MU<AttributeNode>("attr1"))
                                                      .setResult(Int64ResultNode(22))))));

    { // level 0 only
        Grouping request = baseRequest.unchain().setFirstLevel(0).setLastLevel(0);
        EXPECT_TRUE(testAggregation(ctx, request, done0));
    }
    { // level 0 and 1
        Grouping request = baseRequest.unchain().setFirstLevel(0).setLastLevel(1);
        EXPECT_TRUE(testAggregation(ctx, request, done1));
    }
    { // level 0,1 and 2
        Grouping request = baseRequest.unchain().setFirstLevel(0).setLastLevel(2);
        EXPECT_TRUE(testAggregation(ctx, request, done2));
    }
    { // level 0,1,2 and 3
        Grouping request = baseRequest.unchain().setFirstLevel(0).setLastLevel(3);
        EXPECT_TRUE(testAggregation(ctx, request, done3));
    }
    { // level 1 with level 0 as input
        Grouping request = baseRequest.unchain().setFirstLevel(1).setLastLevel(1).setRoot(done0);
        EXPECT_TRUE(testAggregation(ctx, request, done1));
    }
    { // level 2 with level 0 and 1 as input
        Grouping request = baseRequest.unchain().setFirstLevel(2).setLastLevel(2).setRoot(done1);
        EXPECT_TRUE(testAggregation(ctx, request, done2));
    }
    { // level 3 with level 0,1 and 2 as input
        Grouping request = baseRequest.unchain().setFirstLevel(3).setLastLevel(3).setRoot(done2);
        EXPECT_TRUE(testAggregation(ctx, request, done3));
    }
    { // level 2 and 3 with level 0 and 1 as input
        Grouping request = baseRequest.unchain().setFirstLevel(2).setLastLevel(3).setRoot(done1);
        EXPECT_TRUE(testAggregation(ctx, request, done3));
    }
    { // level 1 without level 0 as input
        Grouping request = baseRequest.unchain().setFirstLevel(1).setLastLevel(1);
        EXPECT_TRUE(testAggregation(ctx, request, notDone));
    }
}

/**
 * Verify that the aggregation step does not create more groups than
 * indicated by the maxgroups parameter.
 **/
TEST(GroupingTest, testAggregationMaxGroups)
{
    AggregationContext ctx;
    ctx.add(IntAttrBuilder("attr").add(5).add(10).add(15).sp());
    ctx.result().add(0).add(1).add(2);

    Grouping baseRequest = Grouping().addLevel(createGL(MU<AttributeNode>("attr")));

    Group empty = Group();
    Group grp1 = empty.unchain().addChild(Group().setId(Int64ResultNode(5)));
    Group grp2 = grp1.unchain().addChild(Group().setId(Int64ResultNode(10)));
    Group grp3 = grp2.unchain().addChild(Group().setId(Int64ResultNode(15)));

    { // max 0 groups
        Grouping request = baseRequest;
        request.levels()[0].setMaxGroups(0);
        EXPECT_TRUE(testAggregation(ctx, request, empty));
    }
    { // max 1 groups
        Grouping request = baseRequest;
        request.levels()[0].setMaxGroups(1);
        EXPECT_TRUE(testAggregation(ctx, request, grp1));
    }
    { // max 2 groups
        Grouping request = baseRequest;
        request.levels()[0].setMaxGroups(2);
        EXPECT_TRUE(testAggregation(ctx, request, grp2));
    }
    { // max 3 groups
        Grouping request = baseRequest;
        request.levels()[0].setMaxGroups(3);
        EXPECT_TRUE(testAggregation(ctx, request, grp3));
    }
    { // max 4 groups
        Grouping request = baseRequest;
        request.levels()[0].setMaxGroups(4);
        EXPECT_TRUE(testAggregation(ctx, request, grp3));
    }
    { // max -1 groups
        Grouping request = baseRequest;
        request.levels()[0].setMaxGroups(-1);
        EXPECT_TRUE(testAggregation(ctx, request, grp3));
    }
}

TEST(GroupingTest, Verify_that_groups_are_sorted_by_group_id)
{
    AggregationContext ctx;
    ctx.add(IntAttrBuilder("attr").add(10).add(25).add(35).add(5).add(20).add(15).add(30).sp());
    ctx.result().add(0).add(1).add(2).add(3).add(4).add(5).add(6);

    Grouping request;
    request.addLevel(createGL(MU<AttributeNode>("attr")));

    Group expect;
    expect.addChild(Group().setId(Int64ResultNode(5)))
          .addChild(Group().setId(Int64ResultNode(10)))
          .addChild(Group().setId(Int64ResultNode(15)))
          .addChild(Group().setId(Int64ResultNode(20)))
          .addChild(Group().setId(Int64ResultNode(25)))
          .addChild(Group().setId(Int64ResultNode(30)))
          .addChild(Group().setId(Int64ResultNode(35)));

    EXPECT_TRUE(testAggregation(ctx, request, expect));
}

TEST(GroupingTest, Verify_that_groups_are_tagged_with_the_appropriate_rank_value)
{
    AggregationContext ctx;
    ctx.add(IntAttrBuilder("attr")
            .add(1).add(1).add(1)
            .add(2).add(2).add(2)
            .add(3).add(3).add(3).sp());
    ctx.result()
        .add(0, 5).add(1, 10).add(2, 15)
        .add(3, 10).add(4, 15).add(5, 5)
        .add(6, 15).add(7, 5).add(8, 10);

    Grouping request = Grouping().addLevel(createGL(MU<AttributeNode>("attr")));

    Group expect = Group()
                   .addChild(Group().setId(Int64ResultNode(1)).setRank(RawRank(15)))
                   .addChild(Group().setId(Int64ResultNode(2)).setRank(RawRank(15)))
                   .addChild(Group().setId(Int64ResultNode(3)).setRank(RawRank(15)));

    EXPECT_TRUE(testAggregation(ctx, request, expect));
}

TEST(GroupingTest, testAggregationGroupCapping)
{
    AggregationContext ctx;
    ctx.add(IntAttrBuilder("attr")
            .add(1).add(2).add(3)
            .add(4).add(5).add(6)
            .add(7).add(8).add(9).sp());
    ctx.result()
        .add(0, 1).add(1, 2).add(2, 3)
        .add(3, 4).add(4, 5).add(5, 6)
        .add(6, 7).add(7, 8).add(8, 9);

    {
        Grouping request = Grouping().addLevel(createGL(MU<AttributeNode>("attr")));

        Group expect;
        expect.addChild(Group().setId(Int64ResultNode(1)).setRank(RawRank(1)))
              .addChild(Group().setId(Int64ResultNode(2)).setRank(RawRank(2)))
              .addChild(Group().setId(Int64ResultNode(3)).setRank(RawRank(3)))
              .addChild(Group().setId(Int64ResultNode(4)).setRank(RawRank(4)))
              .addChild(Group().setId(Int64ResultNode(5)).setRank(RawRank(5)))
              .addChild(Group().setId(Int64ResultNode(6)).setRank(RawRank(6)))
              .addChild(Group().setId(Int64ResultNode(7)).setRank(RawRank(7)))
              .addChild(Group().setId(Int64ResultNode(8)).setRank(RawRank(8)))
              .addChild(Group().setId(Int64ResultNode(9)).setRank(RawRank(9)));

        EXPECT_TRUE(testAggregation(ctx, request, expect));
    }
    {
        Grouping request;
        request.addLevel(createGL(3, MU<AttributeNode>("attr")));

        Group expect;
        expect.addChild(Group().setId(Int64ResultNode(7)).setRank(RawRank(7)))
              .addChild(Group().setId(Int64ResultNode(8)).setRank(RawRank(8)))
              .addChild(Group().setId(Int64ResultNode(9)).setRank(RawRank(9)));

        EXPECT_TRUE(testAggregation(ctx, request, expect));
    }
    {
        Grouping request;
        request.setFirstLevel(0)
                .setLastLevel(1)
                .addLevel(std::move(GroupingLevel().setMaxGroups(3).setExpression(MU<AttributeNode>("attr"))
                                  .addAggregationResult(createAggr<SumAggregationResult>(MU<AttributeNode>("attr")))
                                  .addOrderBy(MU<AggregationRefNode>(0), false)));

        Group expect;
        expect.addChild(Group().setId(Int64ResultNode(7)).setRank(RawRank(7))
                                .addAggregationResult(createNumAggr<SumAggregationResult>(MU<Int64ResultNode>(7), MU<AttributeNode>("attr")))
                                .addOrderBy(MU<AggregationRefNode>(0), false))
              .addChild(Group().setId(Int64ResultNode(8)).setRank(RawRank(8))
                                .addAggregationResult(createNumAggr<SumAggregationResult>(MU<Int64ResultNode>(8), MU<AttributeNode>("attr")))
                                .addOrderBy(MU<AggregationRefNode>(0), false))
              .addChild(Group().setId(Int64ResultNode(9)).setRank(RawRank(9))
                                .addAggregationResult(createNumAggr<SumAggregationResult>(MU<Int64ResultNode>(9), MU<AttributeNode>("attr")))
                                .addOrderBy(MU<AggregationRefNode>(0), false));

        EXPECT_TRUE(testAggregation(ctx, request, expect));
    }
    {
        Grouping request;
        request.setFirstLevel(0)
                .setLastLevel(1)
                .addLevel(std::move(GroupingLevel().setMaxGroups(3).setExpression(MU<AttributeNode>("attr"))
                                  .addAggregationResult(createAggr<SumAggregationResult>(MU<AttributeNode>("attr")))
                                  .addOrderBy(MU<AggregationRefNode>(0), true)));

        Group expect = Group()
                       .addChild(Group().setId(Int64ResultNode(1)).setRank(RawRank(1))
                                         .addAggregationResult(createNumAggr<SumAggregationResult>(MU<Int64ResultNode>(1), MU<AttributeNode>("attr")))
                                         .addOrderBy(MU<AggregationRefNode>(0), true))
                       .addChild(Group().setId(Int64ResultNode(2)).setRank(RawRank(2))
                                         .addAggregationResult(createNumAggr<SumAggregationResult>(MU<Int64ResultNode>(2), MU<AttributeNode>("attr")))
                                         .addOrderBy(MU<AggregationRefNode>(0), true))
                       .addChild(Group().setId(Int64ResultNode(3)).setRank(RawRank(3))
                                         .addAggregationResult(createNumAggr<SumAggregationResult>(MU<Int64ResultNode>(3), MU<AttributeNode>("attr")))
                                         .addOrderBy(MU<AggregationRefNode>(0), true));

        EXPECT_TRUE(testAggregation(ctx, request, expect));
    }
    {
        auto add = std::make_unique<AddFunctionNode>();
        add->addArg(MU<AggregationRefNode>(0));
        add->appendArg(MU<ConstantNode>(MU<Int64ResultNode>(3)));

        Grouping request;
        request.setFirstLevel(0)
                .setLastLevel(1)
                .addLevel(std::move(GroupingLevel().setMaxGroups(3).setExpression(MU<AttributeNode>("attr"))
                                  .addAggregationResult(createAggr<SumAggregationResult>(MU<AttributeNode>("attr")))
                                  .addOrderBy(std::move(add), false)));

        Group expect;
        expect.addChild(Group().setId(Int64ResultNode(7)).setRank(RawRank(7))
                                .addAggregationResult(createNumAggr<SumAggregationResult>(MU<Int64ResultNode>(7), MU<AttributeNode>("attr")))
                                .addOrderBy(AddFunctionNode().appendArg(MU<AggregationRefNode>(0)).appendArg(MU<ConstantNode>(MU<Int64ResultNode>(3))).setResult(Int64ResultNode(10)), false))
              .addChild(Group().setId(Int64ResultNode(8)).setRank(RawRank(8))
                                .addAggregationResult(createNumAggr<SumAggregationResult>(MU<Int64ResultNode>(8), MU<AttributeNode>("attr")))
                                .addOrderBy(AddFunctionNode().appendArg(MU<AggregationRefNode>(0)).appendArg(MU<ConstantNode>(MU<Int64ResultNode>(3))).setResult(Int64ResultNode(11)), false))
              .addChild(Group().setId(Int64ResultNode(9)).setRank(RawRank(9))
                                .addAggregationResult(createNumAggr<SumAggregationResult>(MU<Int64ResultNode>(9), MU<AttributeNode>("attr")))
                                .addOrderBy(AddFunctionNode().appendArg(MU<AggregationRefNode>(0)).appendArg(MU<ConstantNode>(MU<Int64ResultNode>(3))).setResult(Int64ResultNode(12)), false));

        EXPECT_TRUE(testAggregation(ctx, request, expect));
    }

}

//-----------------------------------------------------------------------------

/**
 * Test merging the sum of the values from a single attribute vector
 * that was collected directly into the root node. Consider this a
 * smoke test.
 **/
TEST(GroupingTest, testMergeSimpleSum)
{
    Grouping a  = Grouping()
                  .setRoot(Group()
                           .setId(NullResultNode())
                           .addResult(SumAggregationResult()
                                      .setExpression(MU<AttributeNode>("foo"))
                                      .setResult(Int64ResultNode(20))));

    Grouping b  = Grouping()
                  .setRoot(Group()
                           .setId(NullResultNode())
                           .addResult(SumAggregationResult()
                                      .setExpression(MU<AttributeNode>("foo"))
                                      .setResult(Int64ResultNode(30))));

    Group expect = Group()
                   .setId(NullResultNode())
                   .addResult(SumAggregationResult()
                              .setExpression(MU<AttributeNode>("foo"))
                              .setResult(Int64ResultNode(50)));

    EXPECT_TRUE(testMerge(a, b, expect));
}

TEST(GroupingTest, Verify_that_frozen_levels_are_not_touched_during_merge)
{
    Grouping request;
    request.addLevel(createGL(MU<AttributeNode>("c1"), MU<AttributeNode>("s1")))
           .addLevel(createGL(MU<AttributeNode>("c2"), MU<AttributeNode>("s2")))
           .addLevel(createGL(MU<AttributeNode>("c3"), MU<AttributeNode>("s3")));

    Group a = Group()
              .setId(NullResultNode())
              .addResult(SumAggregationResult()
                         .setExpression(MU<AttributeNode>("s0"))
                         .setResult(Int64ResultNode(5)))
              .addChild(Group()
                        .setId(Int64ResultNode(10))
                        .addResult(SumAggregationResult()
                                   .setExpression(MU<AttributeNode>("s1"))
                                   .setResult(Int64ResultNode(10)))
                        .addChild(Group()
                                  .setId(Int64ResultNode(20))
                                  .addResult(SumAggregationResult()
                                          .setExpression(MU<AttributeNode>("s2"))
                                          .setResult(Int64ResultNode(15)))
                                  .addChild(Group()
                                          .setId(Int64ResultNode(30))
                                          .addResult(SumAggregationResult()
                                                  .setExpression(MU<AttributeNode>("s3"))
                                                  .setResult(Int64ResultNode(20))))));

    Group b = Group()
              .setId(NullResultNode())
              .addResult(SumAggregationResult()
                         .setExpression(MU<AttributeNode>("s0"))
                         .setResult(Int64ResultNode(5)))
              .addChild(Group()
                        .setId(Int64ResultNode(10))
                        .addResult(SumAggregationResult()
                                   .setExpression(MU<AttributeNode>("s1"))
                                   .setResult(Int64ResultNode(10)))
                        .addChild(Group()
                                  .setId(Int64ResultNode(20))
                                  .addResult(SumAggregationResult()
                                          .setExpression(MU<AttributeNode>("s2"))
                                          .setResult(Int64ResultNode(15)))
                                  .addChild(Group()
                                          .setId(Int64ResultNode(30))
                                          .addResult(SumAggregationResult()
                                                  .setExpression(MU<AttributeNode>("s3"))
                                                  .setResult(Int64ResultNode(20))))));

    Group expect_all = Group()
                       .setId(NullResultNode())
                       .addResult(SumAggregationResult()
                                  .setExpression(MU<AttributeNode>("s0"))
                                  .setResult(Int64ResultNode(10)))
                       .addChild(Group()
                                 .setId(Int64ResultNode(10))
                                 .addResult(SumAggregationResult()
                                         .setExpression(MU<AttributeNode>("s1"))
                                         .setResult(Int64ResultNode(20)))
                                 .addChild(Group()
                                         .setId(Int64ResultNode(20))
                                         .addResult(SumAggregationResult()
                                                 .setExpression(MU<AttributeNode>("s2"))
                                                 .setResult(Int64ResultNode(30)))
                                         .addChild(Group()
                                                 .setId(Int64ResultNode(30))
                                                 .addResult(SumAggregationResult()
                                                         .setExpression(MU<AttributeNode>("s3"))
                                                         .setResult(Int64ResultNode(40))))));

    Group expect_0 = Group()
                     .setId(NullResultNode())
                     .addResult(SumAggregationResult()
                                .setExpression(MU<AttributeNode>("s0"))
                                .setResult(Int64ResultNode(5)))
                     .addChild(Group()
                               .setId(Int64ResultNode(10))
                               .addResult(SumAggregationResult()
                                       .setExpression(MU<AttributeNode>("s1"))
                                       .setResult(Int64ResultNode(20)))
                               .addChild(Group()
                                       .setId(Int64ResultNode(20))
                                       .addResult(SumAggregationResult()
                                               .setExpression(MU<AttributeNode>("s2"))
                                               .setResult(Int64ResultNode(30)))
                                       .addChild(Group()
                                               .setId(Int64ResultNode(30))
                                               .addResult(SumAggregationResult()
                                                       .setExpression(MU<AttributeNode>("s3"))
                                                       .setResult(Int64ResultNode(40))))));


    Group expect_1 = Group()
                     .setId(NullResultNode())
                     .addResult(SumAggregationResult()
                                .setExpression(MU<AttributeNode>("s0"))
                                .setResult(Int64ResultNode(5)))
                     .addChild(Group()
                               .setId(Int64ResultNode(10))
                               .addResult(SumAggregationResult()
                                       .setExpression(MU<AttributeNode>("s1"))
                                       .setResult(Int64ResultNode(10)))
                               .addChild(Group()
                                       .setId(Int64ResultNode(20))
                                       .addResult(SumAggregationResult()
                                               .setExpression(MU<AttributeNode>("s2"))
                                               .setResult(Int64ResultNode(30)))
                                       .addChild(Group()
                                               .setId(Int64ResultNode(30))
                                               .addResult(SumAggregationResult()
                                                       .setExpression(MU<AttributeNode>("s3"))
                                                       .setResult(Int64ResultNode(40))))));


    Group expect_2 = Group()
                     .setId(NullResultNode())
                     .addResult(SumAggregationResult()
                                .setExpression(MU<AttributeNode>("s0"))
                                .setResult(Int64ResultNode(5)))
                     .addChild(Group()
                               .setId(Int64ResultNode(10))
                               .addResult(SumAggregationResult()
                                       .setExpression(MU<AttributeNode>("s1"))
                                       .setResult(Int64ResultNode(10)))
                               .addChild(Group()
                                       .setId(Int64ResultNode(20))
                                       .addResult(SumAggregationResult()
                                               .setExpression(MU<AttributeNode>("s2"))
                                               .setResult(Int64ResultNode(15)))
                                       .addChild(Group()
                                               .setId(Int64ResultNode(30))
                                               .addResult(SumAggregationResult()
                                                       .setExpression(MU<AttributeNode>("s3"))
                                                       .setResult(Int64ResultNode(40))))));


    Group expect_3 = Group()
                     .setId(NullResultNode())
                     .addResult(SumAggregationResult()
                                .setExpression(MU<AttributeNode>("s0"))
                                .setResult(Int64ResultNode(5)))
                     .addChild(Group()
                               .setId(Int64ResultNode(10))
                               .addResult(SumAggregationResult()
                                       .setExpression(MU<AttributeNode>("s1"))
                                       .setResult(Int64ResultNode(10)))
                               .addChild(Group()
                                       .setId(Int64ResultNode(20))
                                       .addResult(SumAggregationResult()
                                               .setExpression(MU<AttributeNode>("s2"))
                                               .setResult(Int64ResultNode(15)))
                                       .addChild(Group()
                                               .setId(Int64ResultNode(30))
                                               .addResult(SumAggregationResult()
                                                       .setExpression(MU<AttributeNode>("s3"))
                                                       .setResult(Int64ResultNode(20))))));

    EXPECT_TRUE(testMerge(request.unchain().setFirstLevel(0).setLastLevel(3).setRoot(a),
                         request.unchain().setFirstLevel(0).setLastLevel(3).setRoot(b),
                         expect_all));
    EXPECT_TRUE(testMerge(request.unchain().setFirstLevel(1).setLastLevel(3).setRoot(a),
                         request.unchain().setFirstLevel(1).setLastLevel(3).setRoot(b),
                         expect_0));
    EXPECT_TRUE(testMerge(request.unchain().setFirstLevel(2).setLastLevel(5).setRoot(a),
                         request.unchain().setFirstLevel(2).setLastLevel(5).setRoot(b),
                         expect_1));
    EXPECT_TRUE(testMerge(request.unchain().setFirstLevel(3).setLastLevel(5).setRoot(a),
                         request.unchain().setFirstLevel(3).setLastLevel(5).setRoot(b),
                         expect_2));
    EXPECT_TRUE(testMerge(request.unchain().setFirstLevel(4).setLastLevel(4).setRoot(a),
                         request.unchain().setFirstLevel(4).setLastLevel(4).setRoot(b),
                         expect_3));
}

/**
 * Verify that the number of groups for a level is pruned down to
 * maxGroups, that the remaining groups are the highest ranked ones,
 * and that they are sorted by group id.
 **/
TEST(GroupingTest, testMergeGroups)
{
    Grouping request;
    request.addLevel(createGL(MU<AttributeNode>("attr")));

    Group a = Group()
              .setId(NullResultNode())
              .addChild(Group().setId(StringResultNode("05")).setRank(RawRank(5)))
              .addChild(Group().setId(StringResultNode("10")).setRank(RawRank(5)))   // (2)
              .addChild(Group().setId(StringResultNode("15")).setRank(RawRank(15)))
              .addChild(Group().setId(StringResultNode("40")).setRank(RawRank(100))) // 1
              .addChild(Group().setId(StringResultNode("50")).setRank(RawRank(30))); // 3

    Group b = Group()
              .setId(NullResultNode())
              .addChild(Group().setId(StringResultNode("00")).setRank(RawRank(10)))
              .addChild(Group().setId(StringResultNode("10")).setRank(RawRank(50)))  // 2
              .addChild(Group().setId(StringResultNode("20")).setRank(RawRank(25)))  // 4
              .addChild(Group().setId(StringResultNode("40")).setRank(RawRank(10)))  // (1)
              .addChild(Group().setId(StringResultNode("45")).setRank(RawRank(20))); // 5

    Group expect_3 = Group()
                     .setId(NullResultNode())
                     .addChild(Group().setId(StringResultNode("10")).setRank(RawRank(50)))
                     .addChild(Group().setId(StringResultNode("40")).setRank(RawRank(100)))
                     .addChild(Group().setId(StringResultNode("50")).setRank(RawRank(30)));

    Group expect_5 = Group()
                     .setId(NullResultNode())
                     .addChild(Group().setId(StringResultNode("10")).setRank(RawRank(50)))
                     .addChild(Group().setId(StringResultNode("20")).setRank(RawRank(25)))
                     .addChild(Group().setId(StringResultNode("40")).setRank(RawRank(100)))
                     .addChild(Group().setId(StringResultNode("45")).setRank(RawRank(20)))
                     .addChild(Group().setId(StringResultNode("50")).setRank(RawRank(30)));

    Group expect_all = Group()
                       .setId(NullResultNode())
                       .addChild(Group().setId(StringResultNode("00")).setRank(RawRank(10)))
                       .addChild(Group().setId(StringResultNode("05")).setRank(RawRank( 5)))
                       .addChild(Group().setId(StringResultNode("10")).setRank(RawRank(50)))
                       .addChild(Group().setId(StringResultNode("15")).setRank(RawRank(15)))
                       .addChild(Group().setId(StringResultNode("20")).setRank(RawRank(25)))
                       .addChild(Group().setId(StringResultNode("40")).setRank(RawRank(100)))
                       .addChild(Group().setId(StringResultNode("45")).setRank(RawRank(20)))
                       .addChild(Group().setId(StringResultNode("50")).setRank(RawRank(30)));

    request.levels()[0].setMaxGroups(3);
    EXPECT_TRUE(testMerge(request.unchain().setRoot(a), request.unchain().setRoot(b), expect_3));
    EXPECT_TRUE(testMerge(request.unchain().setRoot(b), request.unchain().setRoot(a), expect_3));
    request.levels()[0].setMaxGroups(5);
    EXPECT_TRUE(testMerge(request.unchain().setRoot(a), request.unchain().setRoot(b), expect_5));
    EXPECT_TRUE(testMerge(request.unchain().setRoot(b), request.unchain().setRoot(a), expect_5));
    request.levels()[0].setMaxGroups(-1);
    EXPECT_TRUE(testMerge(request.unchain().setRoot(a), request.unchain().setRoot(b), expect_all));
    EXPECT_TRUE(testMerge(request.unchain().setRoot(b), request.unchain().setRoot(a), expect_all));
}

/**
 * Merge two relatively complex tree structures and verify that the
 * end result is as expected.
 **/
TEST(GroupingTest, testMergeTrees)
{
    Grouping request;
    request.addLevel(createGL(3, MU<AttributeNode>("c1"), MU<AttributeNode>("s1")))
           .addLevel(createGL(2, MU<AttributeNode>("c2"), MU<AttributeNode>("s2")))
           .addLevel(createGL(1, MU<AttributeNode>("c3"), MU<AttributeNode>("s3")));

    Group a = Group()
              .setId(NullResultNode())
              .addResult(SumAggregationResult()
                         .setExpression(MU<AttributeNode>("s0"))
                         .setResult(Int64ResultNode(100)))
              .addChild(Group().setId(Int64ResultNode(4)).setRank(RawRank(10)))
              .addChild(Group()
                        .setId(Int64ResultNode(5))
                        .setRank(RawRank(5)) // merged with 200 rank node
                        .addResult(SumAggregationResult()
                                   .setExpression(MU<AttributeNode>("s1"))
                                   .setResult(Int64ResultNode(100)))
                        .addChild(Group().setId(Int64ResultNode(4)).setRank(RawRank(10)))
                        .addChild(Group()
                                  .setId(Int64ResultNode(5))
                                  .setRank(RawRank(500))
                                  .addResult(SumAggregationResult()
                                          .setExpression(MU<AttributeNode>("s2"))
                                          .setResult(Int64ResultNode(100)))
                                  .addChild(Group().setId(Int64ResultNode(4)).setRank(RawRank(10)))
                                  .addChild(Group()
                                          .setId(Int64ResultNode(5))
                                          .setRank(RawRank(200))
                                          .addResult(SumAggregationResult()
                                                  .setExpression(MU<AttributeNode>("s3"))
                                                  .setResult(Int64ResultNode(100)))
                                            )
                                  )
                        )
              .addChild(Group().setId(Int64ResultNode(9)).setRank(RawRank(10)))
              .addChild(Group()
                        .setId(Int64ResultNode(10))
                        .setRank(RawRank(100))
                        .addResult(SumAggregationResult()
                                   .setExpression(MU<AttributeNode>("s1"))
                                   .setResult(Int64ResultNode(100)))
                        // dummy child would be picked up here
                        .addChild(Group()
                                  .setId(Int64ResultNode(15))
                                  .setRank(RawRank(200))
                                  .addResult(SumAggregationResult()
                                          .setExpression(MU<AttributeNode>("s2"))
                                          .setResult(Int64ResultNode(100)))
                                  .addChild(Group().setId(Int64ResultNode(14)).setRank(RawRank(10)))
                                  .addChild(Group()
                                          .setId(Int64ResultNode(15))
                                          .setRank(RawRank(300))
                                          .addResult(SumAggregationResult()
                                                  .setExpression(MU<AttributeNode>("s3"))
                                                  .setResult(Int64ResultNode(100)))
                                            )
                                  )
                        )
              .addChild(Group().setId(Int64ResultNode(14)).setRank(RawRank(10)))
              .addChild(Group()
                        .setId(Int64ResultNode(15))
                        .setRank(RawRank(300))
                        .addResult(SumAggregationResult()
                                   .setExpression(MU<AttributeNode>("s1"))
                                   .setResult(Int64ResultNode(100)))
                        .addChild(Group().setId(Int64ResultNode(19)).setRank(RawRank(10)))
                        .addChild(Group()
                                  .setId(Int64ResultNode(20))
                                  .setRank(RawRank(100))
                                  .addResult(SumAggregationResult()
                                          .setExpression(MU<AttributeNode>("s2"))
                                          .setResult(Int64ResultNode(100)))
                                  )
                        );

    Group b = Group()
              .setId(NullResultNode())
              .addResult(SumAggregationResult()
                         .setExpression(MU<AttributeNode>("s0"))
                         .setResult(Int64ResultNode(100)))
              .addChild(Group().setId(Int64ResultNode(4)).setRank(RawRank(10)))
              .addChild(Group()
                        .setId(Int64ResultNode(5))
                        .setRank(RawRank(200))
                        .addResult(SumAggregationResult()
                                   .setExpression(MU<AttributeNode>("s1"))
                                   .setResult(Int64ResultNode(100)))
                        .addChild(Group().setId(Int64ResultNode(9)).setRank(RawRank(10)))
                        .addChild(Group()
                                  .setId(Int64ResultNode(10))
                                  .setRank(RawRank(400))
                                  .addResult(SumAggregationResult()
                                          .setExpression(MU<AttributeNode>("s2"))
                                          .setResult(Int64ResultNode(100)))
                                  .addChild(Group().setId(Int64ResultNode(9)).setRank(RawRank(10)))
                                  .addChild(Group()
                                          .setId(Int64ResultNode(10))
                                          .setRank(RawRank(100))
                                          .addResult(SumAggregationResult()
                                                  .setExpression(MU<AttributeNode>("s3"))
                                                  .setResult(Int64ResultNode(100)))
                                            )
                                  )
                        )
              .addChild(Group().setId(Int64ResultNode(9)).setRank(RawRank(10)))
              .addChild(Group()
                        .setId(Int64ResultNode(10))
                        .setRank(RawRank(100))
                        .addResult(SumAggregationResult()
                                   .setExpression(MU<AttributeNode>("s1"))
                                   .setResult(Int64ResultNode(100)))
                        // dummy child would be picket up here
                        .addChild(Group()
                                  .setId(Int64ResultNode(15))
                                  .setRank(RawRank(200))
                                  .addResult(SumAggregationResult()
                                          .setExpression(MU<AttributeNode>("s2"))
                                          .setResult(Int64ResultNode(100)))
                                  )
                        )
              .addChild(Group().setId(Int64ResultNode(14)).setRank(RawRank(10)))
              .addChild(Group()
                        .setId(Int64ResultNode(15))
                        .setRank(RawRank(5)) // merged with 300 rank node
                        .addResult(SumAggregationResult()
                                   .setExpression(MU<AttributeNode>("s1"))
                                   .setResult(Int64ResultNode(100)))
                        .addChild(Group().setId(Int64ResultNode(19)).setRank(RawRank(10)))
                        .addChild(Group()
                                  .setId(Int64ResultNode(20))
                                  .setRank(RawRank(5)) // merged with 100 rank node
                                  .addResult(SumAggregationResult()
                                          .setExpression(MU<AttributeNode>("s2"))
                                          .setResult(Int64ResultNode(100)))
                                  .addChild(Group().setId(Int64ResultNode(19)).setRank(RawRank(10)))
                                  .addChild(Group()
                                          .setId(Int64ResultNode(20))
                                          .setRank(RawRank(500))
                                          .addResult(SumAggregationResult()
                                                  .setExpression(MU<AttributeNode>("s3"))
                                                  .setResult(Int64ResultNode(100)))
                                            )
                                  )
                        .addChild(Group().setId(Int64ResultNode(24)).setRank(RawRank(10)))
                        .addChild(Group()
                                  .setId(Int64ResultNode(25))
                                  .setRank(RawRank(300))
                                  .addResult(SumAggregationResult()
                                          .setExpression(MU<AttributeNode>("s2"))
                                          .setResult(Int64ResultNode(100)))
                                  .addChild(Group().setId(Int64ResultNode(24)).setRank(RawRank(10)))
                                  .addChild(Group()
                                          .setId(Int64ResultNode(25))
                                          .setRank(RawRank(400))
                                          .addResult(SumAggregationResult()
                                                  .setExpression(MU<AttributeNode>("s3"))
                                                  .setResult(Int64ResultNode(100)))
                                            )
                                  )
                        );

    Group expect = Group()
                   .setId(NullResultNode())
                   .addResult(SumAggregationResult()
                              .setExpression(MU<AttributeNode>("s0"))
                              .setResult(Int64ResultNode(200)))
                   .addChild(Group()
                             .setId(Int64ResultNode(5))
                             .setRank(RawRank(200))
                             .addResult(SumAggregationResult()
                                        .setExpression(MU<AttributeNode>("s1"))
                                        .setResult(Int64ResultNode(200)))
                             .addChild(Group()
                                       .setId(Int64ResultNode(5))
                                       .setRank(RawRank(500))
                                       .addResult(SumAggregationResult()
                                               .setExpression(MU<AttributeNode>("s2"))
                                               .setResult(Int64ResultNode(100)))
                                       .addChild(Group()
                                               .setId(Int64ResultNode(5))
                                               .setRank(RawRank(200))
                                               .addResult(SumAggregationResult()
                                                       .setExpression(MU<AttributeNode>("s3"))
                                                       .setResult(Int64ResultNode(100)))
                                                 )
                                       )
                             .addChild(Group()
                                       .setId(Int64ResultNode(10))
                                       .setRank(RawRank(400))
                                       .addResult(SumAggregationResult()
                                               .setExpression(MU<AttributeNode>("s2"))
                                               .setResult(Int64ResultNode(100)))
                                       .addChild(Group()
                                               .setId(Int64ResultNode(10))
                                               .setRank(RawRank(100))
                                               .addResult(SumAggregationResult()
                                                       .setExpression(MU<AttributeNode>("s3"))
                                                       .setResult(Int64ResultNode(100)))
                                                 )
                                       )
                             )
                   .addChild(Group()
                             .setId(Int64ResultNode(10))
                             .setRank(RawRank(100))
                             .addResult(SumAggregationResult()
                                        .setExpression(MU<AttributeNode>("s1"))
                                        .setResult(Int64ResultNode(200)))
                             .addChild(Group()
                                       .setId(Int64ResultNode(15))
                                       .setRank(RawRank(200))
                                       .addResult(SumAggregationResult()
                                               .setExpression(MU<AttributeNode>("s2"))
                                               .setResult(Int64ResultNode(200)))
                                       .addChild(Group()
                                               .setId(Int64ResultNode(15))
                                               .setRank(RawRank(300))
                                               .addResult(SumAggregationResult()
                                                       .setExpression(MU<AttributeNode>("s3"))
                                                       .setResult(Int64ResultNode(100)))
                                                 )
                                       )
                             )
                   .addChild(Group()
                             .setId(Int64ResultNode(15))
                             .setRank(RawRank(300))
                             .addResult(SumAggregationResult()
                                        .setExpression(MU<AttributeNode>("s1"))
                                        .setResult(Int64ResultNode(200)))
                             .addChild(Group()
                                       .setId(Int64ResultNode(20))
                                       .setRank(RawRank(100))
                                       .addResult(SumAggregationResult()
                                               .setExpression(MU<AttributeNode>("s2"))
                                               .setResult(Int64ResultNode(200)))
                                       .addChild(Group()
                                               .setId(Int64ResultNode(20))
                                               .setRank(RawRank(500))
                                               .addResult(SumAggregationResult()
                                                       .setExpression(MU<AttributeNode>("s3"))
                                                       .setResult(Int64ResultNode(100)))
                                                 )
                                       )
                             .addChild(Group()
                                       .setId(Int64ResultNode(25))
                                       .setRank(RawRank(300))
                                       .addResult(SumAggregationResult()
                                               .setExpression(MU<AttributeNode>("s2"))
                                               .setResult(Int64ResultNode(100)))
                                       .addChild(Group()
                                               .setId(Int64ResultNode(25))
                                               .setRank(RawRank(400))
                                               .addResult(SumAggregationResult()
                                                       .setExpression(MU<AttributeNode>("s3"))
                                                       .setResult(Int64ResultNode(100)))
                                                 )
                                       )
                             );

    EXPECT_TRUE(testMerge(request.unchain().setRoot(a), request.unchain().setRoot(b), expect));
    EXPECT_TRUE(testMerge(request.unchain().setRoot(b), request.unchain().setRoot(a), expect));
}

TEST(GroupingTest, testPruneComplex)
{
    { // First level
        Group baseTree = Group()
                         .addChild(Group().setId(StringResultNode("bar0"))
                                   .addChild(Group().setId(StringResultNode("bar00"))
                                             .addChild(Group().setId(StringResultNode("bar000")))
                                             .addChild(Group().setId(StringResultNode("bar001")))
                                             .addChild(Group().setId(StringResultNode("bar002"))))
                                   .addChild(Group().setId(StringResultNode("bar01"))))
                         .addChild(Group().setId(StringResultNode("baz0"))
                                   .addChild(Group().setId(StringResultNode("baz00"))
                                             .addChild(Group().setId(StringResultNode("baz000")))
                                             .addChild(Group().setId(StringResultNode("baz001")))))
                         .addChild(Group().setId(StringResultNode("foo0"))
                                   .addChild(Group().setId(StringResultNode("foo00")))
                                   .addChild(Group().setId(StringResultNode("foo01"))));

        Group prune = Group()
                      .addChild(Group().setId(StringResultNode("bar0")))
                      .addChild(Group().setId(StringResultNode("foo0")));

        Group expect = Group()
                       .addChild(Group().setId(StringResultNode("bar0"))
                                 .addChild(Group().setId(StringResultNode("bar00"))
                                           .addChild(Group().setId(StringResultNode("bar000")))
                                           .addChild(Group().setId(StringResultNode("bar001")))
                                           .addChild(Group().setId(StringResultNode("bar002"))))
                                 .addChild(Group().setId(StringResultNode("bar01"))))
                       .addChild(Group().setId(StringResultNode("foo0"))
                                 .addChild(Group().setId(StringResultNode("foo00")))
                                 .addChild(Group().setId(StringResultNode("foo01"))));
        Grouping request = Grouping().setFirstLevel(1).setLastLevel(1);
        Grouping baseRequest = Grouping().setFirstLevel(0).setLastLevel(3);
        EXPECT_TRUE(testPrune(baseRequest.unchain().setRoot(baseTree), request.unchain().setRoot(prune), expect));
    }
    { // Second level
        Group baseTree = Group()
                         .addChild(Group().setId(StringResultNode("bar0"))
                                   .addChild(Group().setId(StringResultNode("bar00"))
                                             .addChild(Group().setId(StringResultNode("bar000")))
                                             .addChild(Group().setId(StringResultNode("bar001")))
                                             .addChild(Group().setId(StringResultNode("bar002"))))
                                   .addChild(Group().setId(StringResultNode("bar01"))))
                         .addChild(Group().setId(StringResultNode("foo0"))
                                   .addChild(Group().setId(StringResultNode("foo00")))
                                   .addChild(Group().setId(StringResultNode("foo01"))));

        Group prune = Group()
                      .addChild(Group()
                                .setId(StringResultNode("bar0"))
                                .addChild(Group().setId(StringResultNode("bar00"))))
                      .addChild(Group()
                                .setId(StringResultNode("foo0"))
                                .addChild(Group().setId(StringResultNode("foo01"))));

        Group expect = Group()
                       .addChild(Group().setId(StringResultNode("bar0"))
                                 .addChild(Group().setId(StringResultNode("bar00"))
                                           .addChild(Group().setId(StringResultNode("bar000")))
                                           .addChild(Group().setId(StringResultNode("bar001")))
                                           .addChild(Group().setId(StringResultNode("bar002")))))
                       .addChild(Group().setId(StringResultNode("foo0"))
                                 .addChild(Group().setId(StringResultNode("foo01"))));

        Grouping request = Grouping().setFirstLevel(2).setLastLevel(2);
        Grouping baseRequest = Grouping().setFirstLevel(0).setLastLevel(3);
        EXPECT_TRUE(testPrune(baseRequest.unchain().setRoot(baseTree), request.unchain().setRoot(prune), expect));
    }
    { // Third level
        Group baseTree = Group()
                         .addChild(Group().setId(StringResultNode("bar0"))
                                   .addChild(Group().setId(StringResultNode("bar00"))
                                             .addChild(Group().setId(StringResultNode("bar000")))

                                             .addChild(Group().setId(StringResultNode("bar001")))
                                             .addChild(Group().setId(StringResultNode("bar002")))))
                         .addChild(Group().setId(StringResultNode("foo0"))
                                   .addChild(Group().setId(StringResultNode("foo01"))));
        Group prune = Group()
                      .addChild(Group()
                                .setId(StringResultNode("bar0"))
                                .addChild(Group()
                                          .setId(StringResultNode("bar00"))
                                          .addChild(Group().setId(StringResultNode("bar001")))
                                          .addChild(Group().setId(StringResultNode("bar002")))));

        Group expect = Group()
                       .addChild(Group().setId(StringResultNode("bar0"))
                                 .addChild(Group().setId(StringResultNode("bar00"))
                                           .addChild(Group().setId(StringResultNode("bar001")))
                                           .addChild(Group().setId(StringResultNode("bar002")))));
        Grouping request = Grouping().setFirstLevel(3).setLastLevel(3);
        Grouping baseRequest = Grouping().setFirstLevel(0).setLastLevel(3);
        EXPECT_TRUE(testPrune(baseRequest.unchain().setRoot(baseTree), request.unchain().setRoot(prune), expect));
    }
    { // Try pruning a grouping we don't have
        Group baseTree = Group()
                         .addChild(Group().setId(StringResultNode("bar0"))
                                   .addChild(Group().setId(StringResultNode("bar00"))
                                             .addChild(Group().setId(StringResultNode("bar000")))
                                             .addChild(Group().setId(StringResultNode("bar001")))
                                             .addChild(Group().setId(StringResultNode("bar002"))))
                                   .addChild(Group().setId(StringResultNode("bar01"))))
                         .addChild(Group().setId(StringResultNode("baz0"))
                                   .addChild(Group().setId(StringResultNode("baz00"))
                                             .addChild(Group().setId(StringResultNode("baz000")))
                                             .addChild(Group().setId(StringResultNode("baz001")))))
                         .addChild(Group().setId(StringResultNode("foo0"))
                                   .addChild(Group().setId(StringResultNode("foo00")))
                                   .addChild(Group().setId(StringResultNode("foo01"))));

        Group prune = Group()
                      .addChild(Group().setId(StringResultNode("bar0")))
                      .addChild(Group().setId(StringResultNode("boz0")))
                      .addChild(Group().setId(StringResultNode("foo0")))
                      .addChild(Group().setId(StringResultNode("goo0")));

        Group expect = Group()
                       .addChild(Group().setId(StringResultNode("bar0"))
                                 .addChild(Group().setId(StringResultNode("bar00"))
                                           .addChild(Group().setId(StringResultNode("bar000")))
                                           .addChild(Group().setId(StringResultNode("bar001")))
                                           .addChild(Group().setId(StringResultNode("bar002"))))
                                 .addChild(Group().setId(StringResultNode("bar01"))))
                       .addChild(Group().setId(StringResultNode("foo0"))
                                 .addChild(Group().setId(StringResultNode("foo00")))
                                 .addChild(Group().setId(StringResultNode("foo01"))));
        Grouping request = Grouping().setFirstLevel(1).setLastLevel(1);
        Grouping baseRequest = Grouping().setFirstLevel(0).setLastLevel(3);
        EXPECT_TRUE(testPrune(baseRequest.unchain().setRoot(baseTree), request.unchain().setRoot(prune), expect));
    }
}

/**
 * Test partial merge of a grouping tree, where all levels up to "lastLevel" is
 * merged. The last level should not contain any children groups, and only empty
 * results.
 **/
TEST(GroupingTest, testPartialMerging)
{
    Grouping baseRequest;
    baseRequest.addLevel(createGL(MU<AttributeNode>("c1"), MU<AttributeNode>("s1")))
               .addLevel(createGL(MU<AttributeNode>("c2"), MU<AttributeNode>("s2")))
               .addLevel(createGL(MU<AttributeNode>("c3"), MU<AttributeNode>("s3")));

    // Cached result
    Group cached = Group()
              .addResult(SumAggregationResult()
                         .setExpression(MU<AttributeNode>("s0"))
                         .setResult(Int64ResultNode(110)))
              .addChild(Group()
                        .setId(Int64ResultNode(5))
                        .addResult(SumAggregationResult()
                                   .setExpression(MU<AttributeNode>("s1"))
                                   .setResult(Int64ResultNode(10)))
                        .addChild(Group()
                                  .setId(Int64ResultNode(13))
                                  .addResult(SumAggregationResult()
                                          .setExpression(MU<AttributeNode>("s2"))
                                          .setResult(Int64ResultNode(100)))
                                  .addChild(Group()
                                          .setId(Int64ResultNode(14))
                                          .addResult(SumAggregationResult()
                                                  .setExpression(MU<AttributeNode>("s3"))
                                                  .setResult(Int64ResultNode(100)))
                                            )
                                  )
                        )
              .addChild(Group()
                        .setId(Int64ResultNode(10))
                        .addResult(SumAggregationResult()
                                   .setExpression(MU<AttributeNode>("s1"))
                                   .setResult(Int64ResultNode(100)))
                        .addChild(Group()
                                  .setId(Int64ResultNode(15))
                                  .addResult(SumAggregationResult()
                                          .setExpression(MU<AttributeNode>("s2"))
                                          .setResult(Int64ResultNode(100)))
                                  .addChild(Group()
                                          .setId(Int64ResultNode(22))
                                          .addResult(SumAggregationResult()
                                                  .setExpression(MU<AttributeNode>("s3"))
                                                  .setResult(Int64ResultNode(100)))
                                            )
                                  )
                        );


    { // Merge lastlevel 0
        Grouping request = baseRequest.unchain().setFirstLevel(0).setLastLevel(0);
        Group incoming = Group()
                         .addResult(SumAggregationResult()
                                    .setExpression(MU<AttributeNode>("s0"))
                                    .setResult(Int64ResultNode(0)));

        Group expected = Group()
                         .addResult(SumAggregationResult()
                                    .setExpression(MU<AttributeNode>("s0"))
                                    .setResult(Int64ResultNode(110)))
                         .addChild(Group()
                                   .setId(Int64ResultNode(5))
                                   .addResult(SumAggregationResult()
                                              .setExpression(MU<AttributeNode>("s1"))
                                              .setResult(Int64ResultNode(0)))
                                   )
                         .addChild(Group()
                                   .setId(Int64ResultNode(10))
                                   .addResult(SumAggregationResult()
                                              .setExpression(MU<AttributeNode>("s1"))
                                              .setResult(Int64ResultNode(0)))
                                  );
        EXPECT_TRUE(testPartialMerge(request.unchain().setRoot(incoming), request.unchain().setLastLevel(3).setRoot(cached), expected));
    }
    {
        // Merge existing tree. Assume we got modified data down again.
        Grouping request = baseRequest.unchain().setFirstLevel(1).setLastLevel(1);
        Group incoming = Group()
                         .addResult(SumAggregationResult()
                                    .setExpression(MU<AttributeNode>("s0"))
                                    .setResult(Int64ResultNode(200)))
                         .addChild(Group()
                                   .setId(Int64ResultNode(3))
                                   .addResult(SumAggregationResult()
                                              .setExpression(MU<AttributeNode>("s1"))
                                              .setResult(Int64ResultNode(0)))
                                   )
                         .addChild(Group()
                                   .setId(Int64ResultNode(5))
                                   .addResult(SumAggregationResult()
                                              .setExpression(MU<AttributeNode>("s1"))
                                              .setResult(Int64ResultNode(0)))
                                   )
                         .addChild(Group()
                                   .setId(Int64ResultNode(7))
                                   .addResult(SumAggregationResult()
                                              .setExpression(MU<AttributeNode>("s1"))
                                              .setResult(Int64ResultNode(0)))
                                   )
                         .addChild(Group()
                                   .setId(Int64ResultNode(10))
                                   .addResult(SumAggregationResult()
                                              .setExpression(MU<AttributeNode>("s1"))
                                              .setResult(Int64ResultNode(0))))
                         .addChild(Group()
                                   .setId(Int64ResultNode(33))
                                   .addResult(SumAggregationResult()
                                              .setExpression(MU<AttributeNode>("s1"))
                                              .setResult(Int64ResultNode(0)))
                                   );
        Group expected = Group()
                  .addResult(SumAggregationResult()
                             .setExpression(MU<AttributeNode>("s0"))
                             .setResult(Int64ResultNode(200)))
                  .addChild(Group()
                            .setId(Int64ResultNode(3))
                            .addResult(SumAggregationResult()
                                       .setExpression(MU<AttributeNode>("s1"))
                                       .setResult(Int64ResultNode(0)))
                            )
                  .addChild(Group()
                            .setId(Int64ResultNode(5))
                            .addResult(SumAggregationResult()
                                       .setExpression(MU<AttributeNode>("s1"))
                                       .setResult(Int64ResultNode(10)))
                            .addChild(Group()
                                      .setId(Int64ResultNode(13))
                                      .addResult(SumAggregationResult()
                                              .setExpression(MU<AttributeNode>("s2"))
                                              .setResult(Int64ResultNode(0)))
                                      )
                            )
                  .addChild(Group()
                            .setId(Int64ResultNode(7))
                            .addResult(SumAggregationResult()
                                       .setExpression(MU<AttributeNode>("s1"))
                                       .setResult(Int64ResultNode(0)))
                            )
                  .addChild(Group()
                            .setId(Int64ResultNode(10))
                            .addResult(SumAggregationResult()
                                       .setExpression(MU<AttributeNode>("s1"))
                                       .setResult(Int64ResultNode(100)))
                            .addChild(Group()
                                      .setId(Int64ResultNode(15))
                                      .addResult(SumAggregationResult()
                                              .setExpression(MU<AttributeNode>("s2"))
                                              .setResult(Int64ResultNode(0)))
                                      )
                            )
                  .addChild(Group()
                            .setId(Int64ResultNode(33))
                            .addResult(SumAggregationResult()
                                       .setExpression(MU<AttributeNode>("s1"))
                                       .setResult(Int64ResultNode(0)))
                            );
        EXPECT_TRUE(testPartialMerge(request.unchain().setRoot(incoming), request.unchain().setFirstLevel(0).setLastLevel(3).setRoot(cached), expected));
    }
}

TEST(GroupingTest, Test_that_pruning_a_simple_grouping_tree_works)
{
    {
        Grouping request;
        request.addLevel(createGL(MU<AttributeNode>("attr"))).setFirstLevel(1).setLastLevel(1);

        Group a = Group()
                  .addChild(Group().setId(StringResultNode("foo")))
                  .addChild(Group().setId(StringResultNode("bar")))
                  .addChild(Group().setId(StringResultNode("baz")));

        Group b = Group().addChild(Group().setId(StringResultNode("foo")));

        Group expect = Group().addChild(Group().setId(StringResultNode("foo")));

        EXPECT_TRUE(testPrune(request.unchain().setFirstLevel(0).setRoot(a), request.unchain().setRoot(b), expect));
    }
}

TEST(GroupingTest, Test_that_simple_counting_works_as_long_as_we_use_an_expression_that_we_init_calculate_and_ignore)
{
    AggregationContext ctx;
    ctx.result().add(0).add(1).add(2);
    ctx.add(IntAttrBuilder("foo").add(3).add(7).add(15).sp());

    Grouping request;
    request.setRoot(Group().addResult(CountAggregationResult().setExpression(MU<ConstantNode>(MU<Int64ResultNode>(0)))));
    {
        Group expect;
        expect.addResult(CountAggregationResult().setCount(3).setExpression(MU<ConstantNode>(MU<Int64ResultNode>(0))));

        EXPECT_TRUE(testAggregation(ctx, request, expect));
    }
    {
        Group expect = Group().addResult(CountAggregationResult()
                                                 .setCount(1)
                                                 .setExpression(MU<ConstantNode>(MU<Int64ResultNode>(0))));

        EXPECT_TRUE(testAggregation(ctx, request.setTopN(1), expect));
    }
    {
        Grouping request2;
        request2.addLevel(std::move(GroupingLevel()
                                  .addAggregationResult(MU<SumAggregationResult>())
                                  .addOrderBy(MU<AggregationRefNode>(0), false)));
        EXPECT_TRUE(request2.needResort());
        request2.setTopN(0);
        EXPECT_TRUE(request2.needResort());
        request2.setTopN(1);
        EXPECT_TRUE(!request2.needResort());
        request2.setTopN(100);
        EXPECT_TRUE(!request2.needResort());
    }
}

/**
 * Test that simple counting works as long as we use an expression
 * that we init, calculate and ignore.
 **/
TEST(GroupingTest, testCount)
{
    AggregationContext ctx;
    ctx.result().add(0).add(1).add(2);
    ctx.add(IntAttrBuilder("foo").add(3).add(7).add(15).sp());

    Grouping request;
    request.setRoot(Group().addResult(CountAggregationResult().setExpression(MU<ConstantNode>(MU<Int64ResultNode>(0)))));

    Group expect;
    expect.addResult(CountAggregationResult().setCount(3).setExpression(MU<ConstantNode>(MU<Int64ResultNode>(0))));

    EXPECT_TRUE(testAggregation(ctx, request, expect));
}

TEST(GroupingTest, testFS4HitCollection)
{
    { // aggregation
        AggregationContext ctx;
        ctx.result().add(30, 30.0).add(20, 20.0).add(10, 10.0).add(5, 5.0).add(25, 25.0);

        Grouping request = Grouping()
                           .setRoot(Group()
                                    .addResult(HitsAggregationResult()
                                            .setMaxHits(3)
                                            .setExpression(MU<ConstantNode>(MU<Int64ResultNode>(0))))
                                    );

        Group expect = Group()
                       .addResult(HitsAggregationResult()
                                  .setMaxHits(3)
                                  .addHit(FS4Hit(30, 30.0))
                                  .addHit(FS4Hit(25, 25.0))
                                  .addHit(FS4Hit(20, 20.0))
                                  .sort()
                                  .setExpression(MU<ConstantNode>(MU<Int64ResultNode>(0))));

        EXPECT_TRUE(testAggregation(ctx, request, expect));
    }
    { // merging

        Grouping request = Grouping()
                           .setRoot(Group()
                                    .addResult(HitsAggregationResult()
                                            .setMaxHits(3)
                                            .setExpression(MU<ConstantNode>(MU<Int64ResultNode>(0))))
                                    );

        Group expect = Group()
                       .setId(NullResultNode())
                       .addResult(HitsAggregationResult()
                                  .setMaxHits(3)
                                  .addHit(FS4Hit(30, 30.0))
                                  .addHit(FS4Hit(20, 20.0))
                                  .addHit(FS4Hit(10, 10.0))
                                  .sort()
                                  .setExpression(MU<ConstantNode>(MU<Int64ResultNode>(0))));

        Group a = Group()
                  .setId(NullResultNode())
                  .addResult(HitsAggregationResult()
                             .setMaxHits(3)
                             .addHit(FS4Hit(10, 10.0))
                             .addHit(FS4Hit(1,   5.0))
                             .addHit(FS4Hit(2,   4.0))
                             .sort()
                             .setExpression(MU<ConstantNode>(MU<Int64ResultNode>(0))));

        Group b = Group()
                  .setId(NullResultNode())
                  .addResult(HitsAggregationResult()
                             .setMaxHits(3)
                             .addHit(FS4Hit(20, 20.0))
                             .addHit(FS4Hit(3,   7.0))
                             .addHit(FS4Hit(4,   6.0))
                             .sort()
                             .setExpression(MU<ConstantNode>(MU<Int64ResultNode>(0))));

        Group c = Group()
                  .setId(NullResultNode())
                  .addResult(HitsAggregationResult()
                             .setMaxHits(3)
                             .addHit(FS4Hit(30, 30.0))
                             .addHit(FS4Hit(5,   9.0))
                             .addHit(FS4Hit(6,   8.0))
                             .sort()
                             .setExpression(MU<ConstantNode>(MU<Int64ResultNode>(0))));

        EXPECT_TRUE(testMerge(request.unchain().setRoot(a), request.unchain().setRoot(b), request.unchain().setRoot(c), expect));
        EXPECT_TRUE(testMerge(request.unchain().setRoot(b), request.unchain().setRoot(c), request.unchain().setRoot(a), expect));
        EXPECT_TRUE(testMerge(request.unchain().setRoot(c), request.unchain().setRoot(a), request.unchain().setRoot(b), expect));
    }
    { // count hits (for external object selection)
        HitsAggregationResult dummyHits = HitsAggregationResult()
                                          .setMaxHits(3)
                                          .addHit(FS4Hit(1, 3.0))
                                          .addHit(FS4Hit(2, 2.0))
                                          .addHit(FS4Hit(3, 1.0))
                                          .sort();
        Grouping g = Grouping().setRoot(Group().addResult(dummyHits)
                                        .addChild(Group().addResult(dummyHits)
                                                .addChild(Group().addResult(dummyHits))
                                                  )
                                        .addChild(Group().addResult(dummyHits)
                                                .addChild(Group().addResult(dummyHits)
                                                        .addChild(Group().addResult(dummyHits))
                                                          )
                                                  )
                                        );
        EXPECT_TRUE(checkHits(g, 0, 0, 3));
        EXPECT_TRUE(checkHits(g, 1, 1, 6));
        EXPECT_TRUE(checkHits(g, 2, 2, 6));
        EXPECT_TRUE(checkHits(g, 3, 3, 3));
        EXPECT_TRUE(checkHits(g, 4, 4, 0));

        EXPECT_TRUE(checkHits(g, 0, 1, 9));
        EXPECT_TRUE(checkHits(g, 0, 2, 15));
        EXPECT_TRUE(checkHits(g, 0, 3, 18));
        EXPECT_TRUE(checkHits(g, 0, 4, 18));
        EXPECT_TRUE(checkHits(g, 1, 4, 15));
        EXPECT_TRUE(checkHits(g, 2, 4, 9));
        EXPECT_TRUE(checkHits(g, 3, 4, 3));

        EXPECT_TRUE(checkHits(g, 1, 2, 12));
        EXPECT_TRUE(checkHits(g, 2, 3, 9));
        EXPECT_TRUE(checkHits(g, 3, 4, 3));
        EXPECT_TRUE(checkHits(g, 4, 5, 0));
    }
}

TEST(GroupingTest, testFixedWidthBuckets)
{
    using Int = Int64ResultNode;
    using Float = FloatResultNode;
    using IntBucket = IntegerBucketResultNode;
    using FloatBucket = FloatBucketResultNode;

    // positive int buckets
    EXPECT_TRUE(checkBucket(Int(10), Int(0),   IntBucket(0,10)));
    EXPECT_TRUE(checkBucket(Int(10), Int(5),   IntBucket(0,10)));
    EXPECT_TRUE(checkBucket(Int(10), Int(9),   IntBucket(0,10)));
    EXPECT_TRUE(checkBucket(Int(10), Int(10),  IntBucket(10,20)));
    EXPECT_TRUE(checkBucket(Int(10), Int(299), IntBucket(290,300)));

    // negative int buckets
    EXPECT_TRUE(checkBucket(Int(10), Int(-1),   IntBucket(-10,0)));
    EXPECT_TRUE(checkBucket(Int(10), Int(-5),   IntBucket(-10,0)));
    EXPECT_TRUE(checkBucket(Int(10), Int(-10),  IntBucket(-10,0)));
    EXPECT_TRUE(checkBucket(Int(10), Int(-11),  IntBucket(-20,-10)));
    EXPECT_TRUE(checkBucket(Int(10), Int(-300), IntBucket(-300,-290)));

    // positive float buckets
    EXPECT_TRUE(checkBucket(Int(10), Float(0.0),   FloatBucket(0.0,10.0)));
    EXPECT_TRUE(checkBucket(Int(10), Float(5.0),   FloatBucket(0.0,10.0)));
    EXPECT_TRUE(checkBucket(Int(10), Float(9.0),   FloatBucket(0.0,10.0)));
    EXPECT_TRUE(checkBucket(Int(10), Float(10.0),  FloatBucket(10.0,20.0)));
    EXPECT_TRUE(checkBucket(Int(10), Float(299.0), FloatBucket(290.0,300.0)));

    // negative float buckets
    EXPECT_TRUE(checkBucket(Int(10), Float(-1),          FloatBucket(-10.0,0.0)));
    EXPECT_TRUE(checkBucket(Int(10), Float(-5),          FloatBucket(-10.0,0.0)));
    EXPECT_TRUE(checkBucket(Int(10), Float(-10),         FloatBucket(-10.0,0.0)));
    EXPECT_TRUE(checkBucket(Int(10), Float(-10.0000001), FloatBucket(-20.0,-10.0)));
    EXPECT_TRUE(checkBucket(Int(10), Float(-300),        FloatBucket(-300.0,-290.0)));

    // non-integer bucket width
    EXPECT_TRUE(checkBucket(Float(0.5), Float(0.0),       FloatBucket(0.0,0.5)));
    EXPECT_TRUE(checkBucket(Float(0.5), Float(0.5),       FloatBucket(0.5,1.0)));
    EXPECT_TRUE(checkBucket(Float(0.5), Float(0.4999),    FloatBucket(0.0,0.5)));
    EXPECT_TRUE(checkBucket(Float(0.5), Float(-0.0001),   FloatBucket(-0.5,0.0)));
    EXPECT_TRUE(checkBucket(Float(0.5), Float(-0.5),      FloatBucket(-0.5,0.0)));
    EXPECT_TRUE(checkBucket(Float(0.5), Float(-0.50001),  FloatBucket(-1.0,-0.5)));

    // zero-width buckets
    EXPECT_TRUE(checkBucket(Int(0), Int(7),     IntBucket(7,7)));
    EXPECT_TRUE(checkBucket(Int(0), Float(7.5), FloatBucket(7.5,7.5)));

    // bucket wrap protection
    {
        int64_t x = std::numeric_limits<int64_t>::min();
        int64_t y = std::numeric_limits<int64_t>::max();
        EXPECT_TRUE(checkBucket(Int(1000), Int(x + 5), IntBucket(x, (x/1000) * 1000)));
        EXPECT_TRUE(checkBucket(Int(1000), Int(y - 5), IntBucket((y/1000) * 1000, y)));
    }
}

TEST(GroupingTest, testThatNanIsConverted)
{
    Group g;
    double myNan = std::sqrt(-1);
    g.setRank(myNan);
    // Must have been changed for this to work.
    ASSERT_EQ(g.getRank(), g.getRank());
}

TEST(GroupingTest, testAttributeMapLookup)
{
    AggregationContext ctx;
    ctx.result().add(0).add(1);
    ctx.add(StringArrayAttrBuilder("smap.key").add({"k1", "k2"}).add({"k3", "k4"}).sp());
    ctx.add(IntArrayAttrBuilder("smap.value.weight").add({10, 20}).add({100, 200}).sp());
    ctx.add(StringAttrBuilder("key1").add("k1").add("k4").sp());
    ctx.add(StringAttrBuilder("key2").add("k2").add("k3").sp());
    ctx.add(StringAttrBuilder("key3").add("k3").add("k2").sp());
    testAggregationSimple(ctx, SumAggregationResult(), Int64ResultNode(10 + undefinedInteger), "smap{\"k1\"}.weight");
    testAggregationSimple(ctx, SumAggregationResult(), Int64ResultNode(20 + undefinedInteger), "smap{\"k2\"}.weight");
    testAggregationSimple(ctx, SumAggregationResult(), Int64ResultNode(0), "smap{\"k5\"}.weight");
    testAggregationSimple(ctx, SumAggregationResult(), Int64ResultNode(210), "smap{attribute(key1)}.weight");
    testAggregationSimple(ctx, SumAggregationResult(), Int64ResultNode(120), "smap{attribute(key2)}.weight");
    testAggregationSimple(ctx, SumAggregationResult(), Int64ResultNode(0), "smap{attribute(key3)}.weight");
    testAggregationSimple(ctx, MinAggregationResult(), Int64ResultNode(10), "smap{attribute(key1)}.weight");
    testAggregationSimple(ctx, MinAggregationResult(), Int64ResultNode(20), "smap{attribute(key2)}.weight");
    testAggregationSimple(ctx, MaxAggregationResult(), Int64ResultNode(200), "smap{attribute(key1)}.weight");
    testAggregationSimple(ctx, MaxAggregationResult(), Int64ResultNode(100), "smap{attribute(key2)}.weight");
}

TEST(GroupingTest, test_that_non_attributes_are_converted_to_document_field_nodes) {
    AggregationContext ctx;
    ctx.add(IntAttrBuilder("attr").sp());

    Grouping attrRequest;
    attrRequest.setRoot(Group().addResult(SumAggregationResult().setExpression(MU<AttributeNode>("attr"))));
    aggregation::NonAttribute2DocumentAccessor optional2DocumentAccessor(ctx.attrCtx());
    attrRequest.select(optional2DocumentAccessor, optional2DocumentAccessor);
    EXPECT_TRUE(attrRequest.getRoot().getAggregationResult(0).getExpression()->inherits(AttributeNode::classId));

    Grouping nonAttrRequest;
    nonAttrRequest.setRoot(Group().addResult(SumAggregationResult().setExpression(MU<AttributeNode>("non-attr"))));
    nonAttrRequest.select(optional2DocumentAccessor, optional2DocumentAccessor);
    EXPECT_TRUE(nonAttrRequest.getRoot().getAggregationResult(0).getExpression()->inherits(DocumentFieldNode::classId));
}

TEST(GroupingTest, test_that_attributes_can_be_unconditionally_converted_to_document_field_nodes) {
    AggregationContext ctx;
    ctx.add(IntAttrBuilder("attr").sp());

    Grouping attrRequest;
    attrRequest.setRoot(Group().addResult(SumAggregationResult().setExpression(MU<AttributeNode>("attr"))));
    aggregation::Attribute2DocumentAccessor attr2DocumentAccessor;
    attrRequest.select(attr2DocumentAccessor, attr2DocumentAccessor);
    EXPECT_TRUE(attrRequest.getRoot().getAggregationResult(0).getExpression()->inherits(DocumentFieldNode::classId));
}

TEST(GroupingTest, test_bad_grouping)
{
    Grouping baseRequest;
    /*
     * The children ids of a group must be strictly increasing. A group violating this invariant
     * is considered bad. Currently, Group::onSerialize() and Group::onDeserialize() validates the id order.
     */
    baseRequest.setRoot(Group().addChild(Group().setId(FloatBucketResultNode(0.2, 0.3)))
        .addChild(Group().setId(FloatBucketResultNode(0.2, 0.3))));
    vespalib::nbostream os;
    vespalib::NBOSerializer nos(os);
    try {
        baseRequest.serialize(nos);
        FAIL() << "Exception not thrown";
    } catch (const vespalib::IllegalArgumentException& e) {
        EXPECT_EQ("Group::Value::validate_id_order: Expected "
                  R"({"child[1].id":{"[type]":"search::expression::FloatBucketResultNode","from":0.2,"to":0.3}})"
                  " > "
                  R"({"child[0].id":{"[type]":"search::expression::FloatBucketResultNode","from":0.2,"to":0.3}})"
                  ", 2 children",
                  e.getMessage());
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
