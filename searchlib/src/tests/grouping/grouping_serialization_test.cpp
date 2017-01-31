// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for grouping_serialization.

#include <vespa/searchlib/aggregation/aggregation.h>
#include <vespa/searchlib/aggregation/expressioncountaggregationresult.h>
#include <vespa/searchlib/aggregation/perdocexpression.h>
#include <vespa/searchlib/expression/getdocidnamespacespecificfunctionnode.h>
#include <vespa/searchlib/expression/getymumchecksumfunctionnode.h>
#include <vespa/searchlib/expression/documentfieldnode.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <fstream>
#include <vespa/log/log.h>
LOG_SETUP("grouping_serialization_test");

using search::HitRank;
using vespalib::Identifiable;
using vespalib::NBOSerializer;
using vespalib::make_string;
using vespalib::nbostream;
using namespace search::aggregation;
using namespace search::expression;

namespace {

document::GlobalId getGlobalId(uint32_t docId) {
    return document::DocumentId(vespalib::make_string("doc:test:%u", docId))
        .getGlobalId();
}

struct Fixture {
    // Set WRITE_FILES to true to generate new expected serialization files.
    const bool WRITE_FILES = false;
    const std::string file_path = TEST_PATH("../../test/files/");
    std::string file_name;
    std::ifstream file_stream;

    Fixture(const std::string &file_name_in)
        : file_name(file_path + file_name_in),
          file_stream(file_name.c_str(),
                      std::ifstream::in | std::ifstream::binary) {
        if (WRITE_FILES) {
            std::ofstream out(file_name.c_str(),
                              std::ofstream::out | std::ofstream::trunc |
                              std::ofstream::binary);
        }
    }

    void checkObject(const Identifiable &obj) {
        if (WRITE_FILES) {
            nbostream stream;
            NBOSerializer serializer(stream);
            serializer << obj;
            std::ofstream out(file_name.c_str(),
                              std::ofstream::out | std::ofstream::app |
                              std::ofstream::binary);
            uint32_t size = stream.size();
            out.write(reinterpret_cast<const char *>(&size), sizeof(size));
            out.write(stream.peek(), stream.size());
        }

        uint32_t size = 0;
        file_stream.read(reinterpret_cast<char *>(&size), sizeof(size));
        nbostream stream;
        for (size_t i = 0; i < size; ++i) {
            char c;
            file_stream.read(&c, sizeof(c));
            stream << c;
        }
        Identifiable::UP newObj = Identifiable::create(stream);

        if (!EXPECT_TRUE(newObj.get() != 0)) {
            LOG(error, "object of class '%s' resulted in empty echo",
                obj.getClass().name());
            return;
        }
        if (EXPECT_EQUAL(obj.asString(), newObj->asString())
            && EXPECT_TRUE(newObj->cmp(obj) == 0)
            && EXPECT_TRUE(obj.cmp(*newObj) == 0))
        {
            LOG(info, "object of class '%s' passed echo test : %s",
                obj.getClass().name(), newObj->asString().c_str());
        } else {
            LOG(error, "object of class '%s' FAILED echo test",
                obj.getClass().name());
        }
    }
};

//-----------------------------------------------------------------------------

ExpressionNode::CP createDummyExpression() {
    return AddFunctionNode().addArg(ConstantNode(Int64ResultNode(2)))
        .addArg(ConstantNode(Int64ResultNode(2)));
}

//-----------------------------------------------------------------------------

TEST_F("testResultTypes", Fixture("testResultTypes")) {
    f.checkObject(Int64ResultNode(7));
    f.checkObject(FloatResultNode(7.3));
    f.checkObject(StringResultNode("7.3"));
    {
        char tmp[7] = { (char)0xe5, (char)0xa6, (char)0x82, (char)0xe6,
                        (char)0x9e, (char)0x9c,0 };
        f.checkObject(StringResultNode(tmp));
    }
    {
        char tmp[] = { '7', '.', '4' };
        f.checkObject(RawResultNode(tmp, 3));
    }
    f.checkObject(IntegerBucketResultNode());
    f.checkObject(FloatBucketResultNode());
    f.checkObject(IntegerBucketResultNode(10, 20));
    f.checkObject(FloatBucketResultNode(10.0, 20.0));
    f.checkObject(StringBucketResultNode("10.0", "20.0"));
    char tmp[] = { 1, 0, 0};
    char tmp2[] = { 1, 1, 0};
    f.checkObject(
            RawBucketResultNode(ResultNode::UP(new RawResultNode(tmp, 3)),
                                ResultNode::UP(new RawResultNode(tmp2, 3))));

    IntegerBucketResultNodeVector iv;
    iv.getVector().push_back(IntegerBucketResultNode(878, 3246823));
    f.checkObject(iv);

    FloatBucketResultNodeVector fv;
    fv.getVector().push_back(FloatBucketResultNode(878, 3246823));
    f.checkObject(fv);

    StringBucketResultNodeVector sv;
    sv.getVector().push_back(StringBucketResultNode("878", "3246823"));
    f.checkObject(sv);

    RawBucketResultNodeVector rv;
    rv.getVector().push_back(
            RawBucketResultNode(ResultNode::UP(new RawResultNode(tmp, 3)),
                                ResultNode::UP(new RawResultNode(tmp2, 3))));
    f.checkObject(rv);
}

TEST_F("testSpecialNodes", Fixture("testSpecialNodes")) {
    f.checkObject(AttributeNode("testattribute"));
    f.checkObject(DocumentFieldNode("testdocumentfield"));
    {
        f.checkObject(GetDocIdNamespaceSpecificFunctionNode(
                        ResultNode::UP(new Int64ResultNode(7))));
    }
    f.checkObject(GetYMUMChecksumFunctionNode());
}

TEST_F("testFunctionNodes", Fixture("testFunctionNodes")) {
    f.checkObject(AddFunctionNode()
                .addArg(ConstantNode(Int64ResultNode(7)))
                .addArg(ConstantNode(Int64ResultNode(8)))
                .addArg(ConstantNode(Int64ResultNode(9))));
    f.checkObject(XorFunctionNode()
                .addArg(ConstantNode(Int64ResultNode(7)))
                .addArg(ConstantNode(Int64ResultNode(8)))
                .addArg(ConstantNode(Int64ResultNode(9))));
    f.checkObject(MultiplyFunctionNode()
                .addArg(ConstantNode(Int64ResultNode(7)))
                .addArg(ConstantNode(Int64ResultNode(8)))
                .addArg(ConstantNode(Int64ResultNode(9))));
    f.checkObject(DivideFunctionNode()
                .addArg(ConstantNode(Int64ResultNode(7)))
                .addArg(ConstantNode(Int64ResultNode(8)))
                .addArg(ConstantNode(Int64ResultNode(9))));
    f.checkObject(ModuloFunctionNode()
                .addArg(ConstantNode(Int64ResultNode(7)))
                .addArg(ConstantNode(Int64ResultNode(8)))
                .addArg(ConstantNode(Int64ResultNode(9))));
    f.checkObject(MinFunctionNode()
                .addArg(ConstantNode(Int64ResultNode(7)))
                .addArg(ConstantNode(Int64ResultNode(8)))
                .addArg(ConstantNode(Int64ResultNode(9))));
    f.checkObject(MaxFunctionNode()
                .addArg(ConstantNode(Int64ResultNode(7)))
                .addArg(ConstantNode(Int64ResultNode(8)))
                .addArg(ConstantNode(Int64ResultNode(9))));
    f.checkObject(TimeStampFunctionNode(ConstantNode(Int64ResultNode(7)),
                                      TimeStampFunctionNode::Hour, true));
    f.checkObject(ZCurveFunctionNode(ConstantNode(Int64ResultNode(7)),
                                   ZCurveFunctionNode::X));
    f.checkObject(ZCurveFunctionNode(ConstantNode(Int64ResultNode(7)),
                                   ZCurveFunctionNode::Y));
    f.checkObject(NegateFunctionNode(ConstantNode(Int64ResultNode(7))));
    f.checkObject(SortFunctionNode(ConstantNode(Int64ResultNode(7))));
    f.checkObject(NormalizeSubjectFunctionNode(ConstantNode(
                            StringResultNode("foo"))));
    f.checkObject(ReverseFunctionNode(ConstantNode(Int64ResultNode(7))));
    f.checkObject(MD5BitFunctionNode(ConstantNode(Int64ResultNode(7)), 64));
    f.checkObject(XorBitFunctionNode(ConstantNode(Int64ResultNode(7)), 64));
    f.checkObject(CatFunctionNode()
                .addArg(ConstantNode(Int64ResultNode(7)))
                .addArg(ConstantNode(Int64ResultNode(8)))
                .addArg(ConstantNode(Int64ResultNode(9))));
    f.checkObject(FixedWidthBucketFunctionNode());
    f.checkObject(FixedWidthBucketFunctionNode(AttributeNode("foo")));
    f.checkObject(FixedWidthBucketFunctionNode(AttributeNode("foo"))
                .setWidth(Int64ResultNode(10)));
    f.checkObject(FixedWidthBucketFunctionNode(AttributeNode("foo"))
                .setWidth(FloatResultNode(10.0)));
    f.checkObject(RangeBucketPreDefFunctionNode());
    f.checkObject(RangeBucketPreDefFunctionNode(AttributeNode("foo")));
    f.checkObject(DebugWaitFunctionNode(ConstantNode(Int64ResultNode(5)),
                                      3.3, false));
}

TEST_F("testAggregatorResults", Fixture("testAggregatorResults")) {
    f.checkObject(SumAggregationResult()
                .setExpression(AttributeNode("attributeA"))
                .setResult(Int64ResultNode(7)));
    f.checkObject(XorAggregationResult()
                .setXor(Int64ResultNode(7))
                .setExpression(AttributeNode("attributeA")));
    f.checkObject(CountAggregationResult()
                .setCount(7)
                .setExpression(AttributeNode("attributeA")));
    f.checkObject(MinAggregationResult()
                .setExpression(AttributeNode("attributeA"))
                .setResult(Int64ResultNode(7)));
    f.checkObject(MaxAggregationResult()
                .setExpression(AttributeNode("attributeA"))
                .setResult(Int64ResultNode(7)));
    f.checkObject(AverageAggregationResult()
                .setExpression(AttributeNode("attributeA"))
                .setResult(Int64ResultNode(7)));
    ExpressionCountAggregationResult expression_count;
    expression_count.setExpression(ConstantNode(Int64ResultNode(67)))
        .aggregate(DocId(42), HitRank(21));
    f.checkObject(expression_count);
}

TEST_F("testHitCollection", Fixture("testHitCollection")) {
    f.checkObject(FS4Hit());
    f.checkObject(FS4Hit(0, 50.0).setGlobalId(getGlobalId(100)));
    f.checkObject(VdsHit());
    f.checkObject(VdsHit("100", 50.0));
    f.checkObject(VdsHit("100", 50.0).setSummary("rawsummary", 10));
    f.checkObject(HitsAggregationResult());
    f.checkObject(HitsAggregationResult()
                .setMaxHits(5)
                .addHit(FS4Hit(0, 1.0).setGlobalId(getGlobalId(10)))
                .addHit(FS4Hit(0, 2.0).setGlobalId(getGlobalId(20)))
                .addHit(FS4Hit(0, 3.0).setGlobalId(getGlobalId(30)))
                .addHit(FS4Hit(0, 4.0).setGlobalId(getGlobalId(40)))
                .addHit(FS4Hit(0, 5.0).setGlobalId(getGlobalId(50)))
                .setExpression(ConstantNode(Int64ResultNode(5))));
    f.checkObject(HitsAggregationResult()
                .setMaxHits(3)
                .addHit(FS4Hit(0, 1.0).setGlobalId(getGlobalId(10))
                        .setDistributionKey(100))
                .addHit(FS4Hit(0, 2.0).setGlobalId(getGlobalId(20))
                        .setDistributionKey(200))
                .addHit(FS4Hit(0, 3.0).setGlobalId(getGlobalId(30))
                        .setDistributionKey(300))
                .setExpression(ConstantNode(Int64ResultNode(5))));
    f.checkObject(HitsAggregationResult()
                .setMaxHits(3)
                .addHit(VdsHit("10", 1.0).setSummary("100", 3))
                .addHit(VdsHit("20", 2.0).setSummary("200", 3))
                .addHit(VdsHit("30", 3.0).setSummary("300", 3))
                .setExpression(ConstantNode(Int64ResultNode(5))));
}

TEST_F("testGroupingLevel", Fixture("testGroupingLevel")) {
    f.checkObject(GroupingLevel()
                .setMaxGroups(100)
                .setExpression(createDummyExpression())
                .addAggregationResult(SumAggregationResult()
                           .setExpression(createDummyExpression())));
}

TEST_F("testGroup", Fixture("testGroup")) {
    f.checkObject(Group());
    f.checkObject(Group().setId(Int64ResultNode(50))
                .setRank(RawRank(10)));
    f.checkObject(Group().setId(Int64ResultNode(100))
                .addChild(Group().setId(Int64ResultNode(110)))
                .addChild(Group().setId(Int64ResultNode(120))
                          .setRank(20.5)
                          .addAggregationResult(SumAggregationResult()
                                  .setExpression(createDummyExpression()))
                          .addAggregationResult(SumAggregationResult()
                                  .setExpression(createDummyExpression())))
                .addChild(Group().setId(Int64ResultNode(130))
                          .addChild(Group().setId(Int64ResultNode(131)))));
}

TEST_F("testGrouping", Fixture("testGrouping")) {
    f.checkObject(Grouping());
    f.checkObject(Grouping()
                .addLevel(GroupingLevel()
                          .setMaxGroups(100)
                          .setExpression(createDummyExpression())
                          .addAggregationResult(SumAggregationResult()
                                  .setExpression(createDummyExpression())))
                .addLevel(GroupingLevel()
                          .setMaxGroups(10)
                          .setExpression(createDummyExpression())
                          .addAggregationResult(SumAggregationResult()
                                  .setExpression(createDummyExpression()))
                          .addAggregationResult(SumAggregationResult()
                                  .setExpression(createDummyExpression()))));
    f.checkObject(Grouping()
                .addLevel(GroupingLevel()
                          .setExpression(AttributeNode("folder"))
                          .addAggregationResult(XorAggregationResult()
                                  .setExpression(MD5BitFunctionNode(
                                                  AttributeNode("docid"), 64)))
                          .addAggregationResult(SumAggregationResult()
                                  .setExpression(MinFunctionNode()
                                          .addArg(AttributeNode("attribute1"))
                                          .addArg(AttributeNode("attribute2")))
                                     )
                          .addAggregationResult(XorAggregationResult()
                                  .setExpression(
                                          XorBitFunctionNode(CatFunctionNode()
                                                  .addArg(GetDocIdNamespaceSpecificFunctionNode())
                                                  .addArg(DocumentFieldNode("folder"))
                                                  .addArg(DocumentFieldNode("flags")), 64)))));
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
