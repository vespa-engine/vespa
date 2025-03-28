// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/aggregation/perdocexpression.h>
#include <vespa/searchlib/aggregation/aggregation.h>
#include <vespa/searchlib/attribute/extendableattributes.h>
#include <vespa/searchlib/attribute/attributemanager.h>
#include <vespa/searchlib/aggregation/fs4hit.h>
#include <vespa/searchlib/expression/fixedwidthbucketfunctionnode.h>
#include <vespa/searchlib/grouping/groupingengine.h>
#include <algorithm>
#include <vespa/vespalib/objects/objectpredicate.h>
#include <vespa/vespalib/objects/objectoperation.h>
#include <vespa/vespalib/util/rusage.h>
#include <csignal>

#include <vespa/log/log.h>
LOG_SETUP("grouping_benchmark");

using namespace vespalib;
using namespace search;
using namespace search::attribute;
using namespace search::expression;
using namespace search::aggregation;
using namespace search::grouping;

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
    AttrBuilder(const std::string &name)
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
    AttributeVector::SP sp() const {
        return _attrSP;
    }
};

using IntAttrBuilder = AttrBuilder<SingleIntegerExtAttribute, int64_t>;
using FloatAttrBuilder = AttrBuilder<SingleFloatExtAttribute, double>;
using StringAttrBuilder = AttrBuilder<SingleStringExtAttribute, const char *>;

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

    AggregationContext(const AggregationContext &);
    AggregationContext &operator=(const AggregationContext &);

public:
    AggregationContext();
    ~AggregationContext();
    ResultBuilder &result() { return _result; }
    void add(AttributeVector::SP attr) {
        _attrMan.add(attr);
    }
    void setup(Grouping &g) {
        g.configureStaticStuff(ConfigureStaticParams(_attrCtx.get(), 0));
    }
};


AggregationContext::AggregationContext()
    : _attrMan(), _result(), _attrCtx(_attrMan.createContext())
{}
AggregationContext::~AggregationContext() = default;
//-----------------------------------------------------------------------------

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

//-----------------------------------------------------------------------------

/**
 * Run the given grouping request and verify that the resulting group
 * tree matches the expected value.
 **/
bool testAggregation(AggregationContext &ctx, const Grouping &request, bool useEngine) {
    Grouping tmp = request; // create local copy
    ctx.setup(tmp);
    if (useEngine) {
        GroupingEngine engine(tmp);
        engine.aggregate(ctx.result().hits(), ctx.result().size());
        Group::UP result = engine.createResult();
    } else {
        tmp.aggregate(ctx.result().hits(), ctx.result().size());
    }
    tmp.cleanupAttributeReferences();
    CheckAttributeReferences attrCheck;
    tmp.select(attrCheck, attrCheck);
    EXPECT_EQUAL(attrCheck._numrefs, 0);
    return true;
}

#define MU std::make_unique

void benchmarkIntegerSum(bool useEngine, size_t numDocs, size_t numQueries, int64_t maxGroups) {
    IntAttrBuilder attrB("attr0");
    for (size_t i=0; i < numDocs; i++) {
        attrB.add(i);
    }
    AggregationContext ctx;
    for(size_t i(0); i < numDocs; i++) {
        ctx.result().add(i, numDocs-i);
    }
    ctx.add(attrB.sp());
    GroupingLevel level;
    level.setExpression(MU<AttributeNode>("attr0")).setMaxGroups(maxGroups);
    level.addResult(SumAggregationResult().setExpression(MU<AttributeNode>("attr0")));
    if (maxGroups >= 0) {
        level.addOrderBy(MU<AggregationRefNode>(0), false);
    }
    Grouping baseRequest;
    baseRequest.setFirstLevel(0)
               .setLastLevel(1)
               .setRoot(Group().addResult(SumAggregationResult().setExpression(MU<AttributeNode>("attr0"))))
               .addLevel(std::move(level));

    for (size_t i(0); i < numQueries; i++) {
        testAggregation(ctx, baseRequest, useEngine);
    }
}

void benchmarkIntegerCount(bool useEngine, size_t numDocs, size_t numQueries, int64_t maxGroups) {
    IntAttrBuilder attrB("attr0");
    for (size_t i=0; i < numDocs; i++) {
        attrB.add(i);
    }
    AggregationContext ctx;
    for(size_t i(0); i < numDocs; i++) {
        ctx.result().add(i);
    }
    ctx.add(attrB.sp());
    GroupingLevel level;
    level.setExpression(MU<AttributeNode>("attr0")).setMaxGroups(maxGroups);
    level.addResult(CountAggregationResult().setExpression(MU<AttributeNode>("attr0")));
    if (maxGroups >= 0) {
        level.addOrderBy(MU<AggregationRefNode>(0), false);
    }
    Grouping baseRequest;
    baseRequest.setFirstLevel(0)
               .setLastLevel(1)
               .setRoot(Group().addResult(CountAggregationResult().setExpression(MU<AttributeNode>("attr0"))))
               .addLevel(std::move(level));

    for (size_t i(0); i < numQueries; i++) {
        testAggregation(ctx, baseRequest, useEngine);
    }
}

TEST_MAIN() {
    size_t numDocs = 1000000;
    size_t numQueries = 1000;
    int64_t maxGroups = -1;
    bool useEngine = true;
    std::string idType = "int";
    std::string aggrType = "sum";
    if (argc > 1) {
        useEngine = (strcmp(argv[1], "tree") != 0);
    }
    if (argc > 2) {
        idType = argv[2];
    }
    if (argc > 3) {
        aggrType = argv[3];
    }
    if (argc > 4) {
        numDocs = strtol(argv[4], nullptr, 0);
    }
    if (argc > 5) {
        numQueries = strtol(argv[5], nullptr, 0);
    }
    if (argc > 6) {
        maxGroups = strtol(argv[6], nullptr, 0);
    }
    LOG(info, "sizeof(Group) = %ld", sizeof(Group));
    LOG(info, "sizeof(ResultNode::CP) = %ld", sizeof(ResultNode::CP));
    LOG(info, "sizeof(RawRank) = %ld", sizeof(RawRank));
    LOG(info, "sizeof(SumAggregationResult) = %ld", sizeof(SumAggregationResult));
    LOG(info, "sizeof(CountAggregationResult) = %ld", sizeof(CountAggregationResult));
    LOG(info, "sizeof(Int64ResultNode) = %ld", sizeof(Int64ResultNode));

    vespalib::steady_time start(vespalib::steady_clock::now());
    if (idType == "int") {
        if (aggrType == "sum") {
            benchmarkIntegerSum(useEngine, numDocs, numQueries, maxGroups);
        } else if (aggrType == "count") {
            benchmarkIntegerCount(useEngine, numDocs, numQueries, maxGroups);
        } else {
            ASSERT_TRUE(false);
        }
    } else {
        ASSERT_TRUE(false);
    }
    LOG(info, "rusage = {\n%s\n}", vespalib::RUsage::createSelf(start).toString().c_str());
    ASSERT_EQUAL(0, kill(0, SIGPROF));
}
