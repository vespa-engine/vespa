// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/log/log.h>
LOG_SETUP("attr_test");

#include <vespa/searchlib/aggregation/perdocexpression.h>
#include <vespa/searchlib/aggregation/aggregation.h>
#include <vespa/searchlib/attribute/extendableattributes.h>
#include <vespa/vespalib/objects/objectdumper.h>
#include <vespa/searchlib/expression/arrayatlookupfunctionnode.h>
#include <vespa/searchlib/expression/interpolatedlookupfunctionnode.h>

using namespace search;
using namespace search::expression;
using namespace vespalib;


struct AttributeFixture {
    
    AttributeGuard guard;

    const double doc0attr[11] = {
        0.1428571428571428,
        0.2539682539682539,
        0.3448773448773448,
        0.4218004218004217,
        0.4884670884670883,
        0.5472906178788530,
        0.5999221968262214,
        0.6475412444452690,
        0.6910195053148342,
        0.7310195053148342,
        0.7680565423518712
    };
    const double doc1attr[11] = {
        0.1408450704225352,
        0.2507351803126450,
        0.3408252704027350,
        0.4171611482653304,
        0.4833863138282443,
        0.5418658459919869,
        0.5942218669343952,
        0.6416152318633051,
        0.6849052751533483,
        0.7247459126035475,
        0.7616462816072375
    };

    AttributeFixture() : guard()
    {
        MultiFloatExtAttribute *attr = new MultiFloatExtAttribute("sortedArrayAttr");
        DocId d = 0;
        
        attr->addDoc(d);
        for (double val : doc0attr) {
            attr->add(val);
        }
        attr->addDoc(d); 
        for (double val : doc1attr) {
            attr->add(val);
        }
        AttributeVector::SP sp(attr);
        guard = AttributeGuard(sp);
    }
};

struct IntAttrFixture {
    AttributeGuard guard;

    const int64_t doc0attr[11] = {
        1,
        333,
        88888888L,
        -17
    };
    const double doc1attr[11] = {
        2,
        -42,
        4444,
        999999999L
    };

    IntAttrFixture() : guard()
    {
        MultiIntegerExtAttribute *attr = new MultiIntegerExtAttribute("sortedArrayAttr");
        DocId d = 0;
        attr->addDoc(d);
        for (int64_t val : doc0attr) {
            attr->add(val);
        }
        attr->addDoc(d); 
        for (int64_t val : doc1attr) {
            attr->add(val);
        }
        AttributeVector::SP sp(attr);
        guard = AttributeGuard(sp);
    }
};

struct StringAttrFixture {
    AttributeGuard guard;
    StringAttrFixture() : guard()
    {
        MultiStringExtAttribute *attr = new MultiStringExtAttribute("sortedArrayAttr");
        DocId d = 0;
        attr->addDoc(d);
        attr->add("1");
        attr->add("333");
        attr->add("88888888");
        attr->addDoc(d); 
        attr->add("2");
        attr->add("4444");
        attr->add("999999999");
        AttributeVector::SP sp(attr);
        guard = AttributeGuard(sp);
    }
};

#define MU std::make_unique

TEST_F("testArrayAt", AttributeFixture()) {
    for (int i = 0; i < 11; i++) {
        ExpressionTree et(MU<ArrayAtLookup>(*f1.guard, MU<ConstantNode>(MU<Int64ResultNode>(i))));
        ExpressionTree::Configure treeConf;
        et.select(treeConf, treeConf);
        EXPECT_TRUE(et.getResult().getClass().inherits(FloatResultNode::classId));

        EXPECT_TRUE(et.execute(0, HitRank(0.0)));
        EXPECT_EQUAL(et.getResult().getFloat(), f1.doc0attr[i]);
        EXPECT_TRUE(et.execute(1, HitRank(0.0)));
        EXPECT_EQUAL(et.getResult().getFloat(), f1.doc1attr[i]);
    }
}

TEST_F("testArrayAtInt", IntAttrFixture()) {
    for (int i = 0; i < 3; i++) {
        auto x = MU<ArrayAtLookup>(*f1.guard, MU<ConstantNode>(MU<Int64ResultNode>(4567)));
        auto y = MU<ArrayAtLookup>(*f1.guard, MU<ConstantNode>(MU<Int64ResultNode>(i)));
        *x = *y;

        ExpressionTree et(std::move(x));
        ExpressionTree::Configure treeConf;
        et.select(treeConf, treeConf);
        EXPECT_TRUE(et.getResult().getClass().inherits(IntegerResultNode::classId));

        EXPECT_TRUE(et.execute(0, HitRank(0.0)));
        EXPECT_EQUAL(et.getResult().getInteger(), f1.doc0attr[i]);
        EXPECT_TRUE(et.execute(1, HitRank(0.0)));
        EXPECT_EQUAL(et.getResult().getInteger(), f1.doc1attr[i]);
    }
}


TEST_F("testArrayAtString", StringAttrFixture()) {
    ExpressionTree et(MU<ArrayAtLookup>(*f1.guard, MU<ConstantNode>(MU<Int64ResultNode>(1))));
    ExpressionTree::Configure treeConf;
    et.select(treeConf, treeConf);
    EXPECT_TRUE(et.getResult().getClass().inherits(StringResultNode::classId));

    char mem[64];
    ResultNode::BufferRef buf(&mem, sizeof(mem));

    EXPECT_TRUE(et.execute(0, HitRank(0.0)));
    EXPECT_EQUAL(et.getResult().getString(buf).c_str(), std::string("333"));

    EXPECT_TRUE(et.execute(1, HitRank(0.0)));
    EXPECT_EQUAL(et.getResult().getString(buf).c_str(), std::string("4444"));
}

struct ArrayAtExpressionFixture :
    public AttributeFixture
{
    ExpressionTree et;

    ArrayAtExpressionFixture(int i) :
        AttributeFixture(),
        et(MU<ArrayAtLookup>(*guard, MU<ConstantNode>(MU<Int64ResultNode>(i))))
    {
        ExpressionTree::Configure treeConf;
        et.select(treeConf, treeConf);
    }
};


TEST_F("testArrayAtBelowRange", ArrayAtExpressionFixture(-1)) {
    EXPECT_TRUE(f1.et.getResult().getClass().inherits(FloatResultNode::classId));

    EXPECT_TRUE(f1.et.execute(0, HitRank(0.0)));
    EXPECT_EQUAL(f1.et.getResult().getFloat(), f1.doc0attr[0]);
    EXPECT_TRUE(f1.et.execute(1, HitRank(0.0)));
    EXPECT_EQUAL(f1.et.getResult().getFloat(), f1.doc1attr[0]);
}

TEST_F("testArrayAtAboveRange", ArrayAtExpressionFixture(17)) {
    EXPECT_TRUE(f1.et.getResult().getClass().inherits(FloatResultNode::classId));

    EXPECT_TRUE(f1.et.execute(0, HitRank(0.0)));
    EXPECT_EQUAL(f1.et.getResult().getFloat(), f1.doc0attr[10]);
    EXPECT_TRUE(f1.et.execute(1, HitRank(0.0)));
    EXPECT_EQUAL(f1.et.getResult().getFloat(), f1.doc1attr[10]);
}

TEST_F("testInterpolatedLookup", AttributeFixture()) {
    ExpressionTree et(MU<InterpolatedLookup>(*f1.guard, MU<ConstantNode>(MU<FloatResultNode>(f1.doc0attr[2]))));
    ExpressionTree::Configure treeConf;
    et.select(treeConf, treeConf);

    EXPECT_TRUE(et.getResult().getClass().inherits(FloatResultNode::classId));

    EXPECT_TRUE(et.execute(0, HitRank(0.0)));
    EXPECT_EQUAL(et.getResult().getFloat(), 2.0);

    EXPECT_TRUE(et.execute(1, HitRank(0.0)));
    EXPECT_EQUAL(et.getResult().getFloat(), 2.053082175617388);
}

TEST_F("testWithRelevance", AttributeFixture()) {
    ExpressionTree et(MU<InterpolatedLookup>(*f1.guard, MU<RelevanceNode>()));
    ExpressionTree::Configure treeConf;
    et.select(treeConf, treeConf);

    EXPECT_TRUE(et.getResult().getClass().inherits(FloatResultNode::classId));

    // docid 0
    double expect0[] = { 0.0, 0.0, 0.0,

                         0.514285714285715012,
                         1.506349206349207659,
                         2.716594516594518005,

                         4.19605949605949835,
                         6.001633866649353166,
                         8.224512367129145574,

                         10.0, 10.0, 10.0 };

    for (int i = 0; i < 12; i++) {
        double r = i-1;
        r *= 0.1;
        TEST_STATE(vespalib::make_string("i=%d", i).c_str());
        EXPECT_TRUE(et.execute(0, HitRank(r)));
        EXPECT_EQUAL(expect0[i], et.getResult().getFloat());
    }

    EXPECT_TRUE(et.execute(0, HitRank(f1.doc0attr[2])));
    EXPECT_EQUAL(et.getResult().getFloat(), 2.0);

    // docid 1
    EXPECT_TRUE(et.execute(1, HitRank(f1.doc1attr[0] - 0.001)));
    EXPECT_EQUAL(et.getResult().getFloat(), 0.0);

    EXPECT_TRUE(et.execute(1, HitRank(f1.doc1attr[0])));
    EXPECT_EQUAL(et.getResult().getFloat(), 0.0);

    EXPECT_TRUE(et.execute(1, HitRank(f1.doc1attr[2])));
    EXPECT_EQUAL(et.getResult().getFloat(), 2.0);
                
    EXPECT_TRUE(et.execute(1, HitRank(f1.doc1attr[4])));
    EXPECT_EQUAL(et.getResult().getFloat(), 4.0);

    EXPECT_TRUE(et.execute(1, HitRank(f1.doc1attr[10])));
    EXPECT_EQUAL(et.getResult().getFloat(), 10.0);

    EXPECT_TRUE(et.execute(1, HitRank(f1.doc1attr[10] + 0.01)));
    EXPECT_EQUAL(et.getResult().getFloat(), 10.0);
}

TEST_MAIN() { TEST_RUN_ALL(); }
