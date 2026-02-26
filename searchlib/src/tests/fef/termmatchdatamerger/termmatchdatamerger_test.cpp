// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/fef/termmatchdatamerger.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::fef;

using MDMI = TermMatchDataMerger::Input;
using MDMIs = TermMatchDataMerger::Inputs;

namespace {

TermFieldMatchDataPosition make_pos(uint32_t pos)
{
    return TermFieldMatchDataPosition(0, pos, 1, 1000);
}

} // namespace <unnamed>

TEST(TermMatchDataMergerTest, merge_empty_input)
{
    TermFieldMatchData out;
    TermFieldMatchDataArray output;
    output.add(&out);

    TermFieldMatchData in;
    MDMIs input;
    input.push_back(MDMI(&in, 1.0));

    TermMatchDataMerger merger(input, output);

    uint32_t docid = 5;
    in.reset(docid);
    merger.merge(docid);
    EXPECT_TRUE(out.has_ranking_data(docid));
    EXPECT_TRUE(out.begin() == out.end());
}

TEST(TermMatchDataMergerTest, merge_simple)
{
    TermFieldMatchData a;
    TermFieldMatchData b;
    TermFieldMatchData c;
    MDMIs input;
    input.push_back(MDMI(&a, 0.5));
    input.push_back(MDMI(&b, 1.0));
    input.push_back(MDMI(&c, 1.5));

    TermFieldMatchData out;
    TermFieldMatchDataArray output;
    output.add(&out);
    TermMatchDataMerger merger(input, output);

    uint32_t docid = 5;

    a.reset(docid);
    a.appendPosition(make_pos(5).setMatchExactness(0.5));
    a.appendPosition(make_pos(10).setMatchExactness(3.0));
    a.appendPosition(make_pos(15).setMatchExactness(2.0));

    b.reset(docid);
    b.appendPosition(make_pos(7).setMatchExactness(0.5));
    b.appendPosition(make_pos(20).setMatchExactness(4.0));

    c.reset(docid);
    c.appendPosition(make_pos(22).setMatchExactness(0.5));
    c.appendPosition(make_pos(27).setMatchExactness(2.0));
    c.appendPosition(make_pos(28).setMatchExactness(5.0));

    merger.merge(docid);

    EXPECT_TRUE(out.has_ranking_data(docid));
    EXPECT_EQ(8u, out.end() - out.begin());

    EXPECT_EQ( 5u, out.begin()[0].getPosition());
    EXPECT_EQ( 7u, out.begin()[1].getPosition());
    EXPECT_EQ(10u, out.begin()[2].getPosition());
    EXPECT_EQ(15u, out.begin()[3].getPosition());
    EXPECT_EQ(20u, out.begin()[4].getPosition());
    EXPECT_EQ(22u, out.begin()[5].getPosition());
    EXPECT_EQ(27u, out.begin()[6].getPosition());
    EXPECT_EQ(28u, out.begin()[7].getPosition());

    EXPECT_EQ(0.25, out.begin()[0].getMatchExactness());
    EXPECT_EQ( 0.5, out.begin()[1].getMatchExactness());
    EXPECT_EQ( 1.5, out.begin()[2].getMatchExactness());
    EXPECT_EQ( 1.0, out.begin()[3].getMatchExactness());
    EXPECT_EQ( 4.0, out.begin()[4].getMatchExactness());
    EXPECT_EQ(0.75, out.begin()[5].getMatchExactness());
    EXPECT_EQ( 3.0, out.begin()[6].getMatchExactness());
    EXPECT_EQ( 7.5, out.begin()[7].getMatchExactness());

    // one stale input

    docid = 10;
    a.reset(docid);
    a.appendPosition(make_pos(5));
    a.appendPosition(make_pos(10));
    a.appendPosition(make_pos(15));

    merger.merge(docid);

    EXPECT_TRUE(out.has_ranking_data(docid));
    EXPECT_EQ(3u, out.end() - out.begin());

    EXPECT_EQ( 5u, out.begin()[0].getPosition());
    EXPECT_EQ(10u, out.begin()[1].getPosition());
    EXPECT_EQ(15u, out.begin()[2].getPosition());

    // both inputs are stale

    docid = 15;

    merger.merge(docid);
    EXPECT_FALSE(out.has_data(docid));
}

TEST(TermMatchDataMergerTest, merge_multiple_fields)
{
    TermFieldMatchData a;
    TermFieldMatchData b;
    TermFieldMatchData c;
    MDMIs input;
    a.setFieldId(1);
    b.setFieldId(2);
    c.setFieldId(2);
    input.push_back(MDMI(&a, 1.0));
    input.push_back(MDMI(&b, 0.5));
    input.push_back(MDMI(&c, 1.5));

    TermFieldMatchData out1;
    TermFieldMatchData out2;
    TermFieldMatchData out3;
    TermFieldMatchDataArray output;
    out1.setFieldId(1);
    out2.setFieldId(2);
    out3.setFieldId(3);
    output.add(&out1).add(&out2).add(&out3);

    TermMatchDataMerger merger(input, output);

    uint32_t docid = 5;

    a.reset(docid);
    a.appendPosition(make_pos(5));
    a.appendPosition(make_pos(15));

    b.reset(docid);
    b.appendPosition(make_pos(7));
    b.appendPosition(make_pos(20));

    c.reset(docid);
    c.appendPosition(make_pos(5));
    c.appendPosition(make_pos(20));

    merger.merge(docid);

    EXPECT_TRUE(out1.has_ranking_data(docid));
    EXPECT_TRUE(out2.has_ranking_data(docid));
    EXPECT_FALSE(out3.has_data(docid));

    EXPECT_EQ(2u, out1.end() - out1.begin());
    EXPECT_EQ(3u, out2.end() - out2.begin());

    EXPECT_EQ( 5u, out1.begin()[0].getPosition());
    EXPECT_EQ(15u, out1.begin()[1].getPosition());

    EXPECT_EQ( 5u, out2.begin()[0].getPosition());
    EXPECT_EQ( 7u, out2.begin()[1].getPosition());
    EXPECT_EQ(20u, out2.begin()[2].getPosition());

    EXPECT_EQ(1.0, out1.begin()[0].getMatchExactness());
    EXPECT_EQ(1.0, out1.begin()[1].getMatchExactness());

    EXPECT_EQ(1.5, out2.begin()[0].getMatchExactness());
    EXPECT_EQ(0.5, out2.begin()[1].getMatchExactness());
    EXPECT_EQ(1.5, out2.begin()[2].getMatchExactness());
}

TEST(TermMatchDataMergerTest, merge_duplicates)
{
    TermFieldMatchData a;
    TermFieldMatchData b;
    MDMIs input;
    input.push_back(MDMI(&a, 0.5));
    input.push_back(MDMI(&b, 1.5));

    TermFieldMatchData out;
    TermFieldMatchDataArray output;
    output.add(&out);
    TermMatchDataMerger merger(input, output);

    uint32_t docid = 5;

    a.reset(docid);
    a.appendPosition(make_pos(5));
    a.appendPosition(make_pos(10));
    a.appendPosition(make_pos(15));

    b.reset(docid);
    b.appendPosition(make_pos(3));
    b.appendPosition(make_pos(10));
    b.appendPosition(make_pos(15));
    b.appendPosition(make_pos(17));

    merger.merge(docid);

    EXPECT_TRUE(out.has_ranking_data(docid));
    EXPECT_EQ(5u, out.end() - out.begin());

    EXPECT_EQ( 3u, out.begin()[0].getPosition());
    EXPECT_EQ(1.5, out.begin()[0].getMatchExactness());
    EXPECT_EQ( 5u, out.begin()[1].getPosition());
    EXPECT_EQ(0.5, out.begin()[1].getMatchExactness());
    EXPECT_EQ(10u, out.begin()[2].getPosition());
    EXPECT_EQ(1.5, out.begin()[2].getMatchExactness());
    EXPECT_EQ(15u, out.begin()[3].getPosition());
    EXPECT_EQ(1.5, out.begin()[3].getMatchExactness());
    EXPECT_EQ(17u, out.begin()[4].getPosition());
    EXPECT_EQ(1.5, out.begin()[4].getMatchExactness());
}

TEST(TermMatchDataMergerTest, merge_max_element_length)
{
    TermFieldMatchData a;
    TermFieldMatchData b;
    MDMIs input;
    input.push_back(MDMI(&a, 1.0));
    input.push_back(MDMI(&b, 1.0));

    TermFieldMatchData out;
    TermFieldMatchDataArray output;
    output.add(&out);
    TermMatchDataMerger merger(input, output);

    uint32_t docid = 5;
    a.reset(docid);
    a.appendPosition(make_pos(1));
    b.reset(docid);
    b.appendPosition(make_pos(2));
    merger.merge(docid);

    EXPECT_TRUE(out.has_ranking_data(docid));
    EXPECT_EQ(1000u, out.getIterator().getFieldLength());
}

class TermMatchDataMergerTest2 : public ::testing::Test
{
protected:
    TermFieldMatchData a;
    TermFieldMatchData b;
    MDMIs input;
    TermFieldMatchData out;
    TermFieldMatchDataArray output;
    TermMatchDataMerger merger;

    TermMatchDataMergerTest2()
        : a(),
          b(),
          input({{&a, 0.5},{&b, 1.5}}),
          out(),
          output(),
          merger(input, output.add(&out))
    {
    }
};

TEST_F(TermMatchDataMergerTest2, merge_no_normal_features)
{
    out.setNeedNormalFeatures(false);

    uint32_t docid = 5;

    a.reset(docid);
    a.appendPosition(make_pos(5));

    b.reset(docid);
    b.appendPosition(make_pos(3));

    merger.merge(docid);
    EXPECT_TRUE(out.has_ranking_data(docid));
    EXPECT_EQ(0u, out.size());
}

TEST_F(TermMatchDataMergerTest2, merge_interleaved_features)
{
    out.setNeedNormalFeatures(false);
    out.setNeedInterleavedFeatures(true);

    uint32_t docid = 5;

    a.reset(docid);
    a.setNumOccs(1);
    a.setFieldLength(30);

    b.reset(docid);
    b.setNumOccs(1);
    b.setFieldLength(35);

    merger.merge(docid);
    EXPECT_TRUE(out.has_ranking_data(docid));
    EXPECT_EQ(2u, out.getNumOccs());
    EXPECT_EQ(35u, out.getFieldLength());
}

TEST_F(TermMatchDataMergerTest2, merge_interleaved_features_with_detected_duplicate)
{
    out.setNeedNormalFeatures(true);
    out.setNeedInterleavedFeatures(true);

    uint32_t docid = 5;

    a.reset(docid);
    a.setNumOccs(1);
    a.setFieldLength(30);
    a.appendPosition(make_pos(5));

    b.reset(docid);
    b.setNumOccs(1);
    b.setFieldLength(30);
    b.appendPosition(make_pos(5));

    merger.merge(docid);
    EXPECT_TRUE(out.has_ranking_data(docid));
    EXPECT_EQ(1u, out.end() - out.begin());
    EXPECT_EQ( 5u, out.begin()[0].getPosition());
    EXPECT_EQ(1.5, out.begin()[0].getMatchExactness());
    EXPECT_EQ(1u, out.getNumOccs());
    EXPECT_EQ(30u, out.getFieldLength());
}

GTEST_MAIN_RUN_ALL_TESTS()
