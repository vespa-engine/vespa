// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("termmatchdatamerger_test");
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/fef/termmatchdatamerger.h>

using namespace search::fef;

typedef TermMatchDataMerger::Input MDMI;
typedef TermMatchDataMerger::Inputs MDMIs;

namespace {

TermFieldMatchDataPosition make_pos(uint32_t pos)
{
    return TermFieldMatchDataPosition(0, pos, 1, 1000);
}

} // namespace <unnamed>

class Test : public vespalib::TestApp
{
public:
    void testMergeEmptyInput();
    void testMergeSimple();
    void testMergeMultifield();
    void testMergeDuplicates();
    void testMergeFieldLength();
    int Main() override;
};

void
Test::testMergeEmptyInput()
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
    EXPECT_EQUAL(docid, out.getDocId());
    EXPECT_TRUE(out.begin() == out.end());
}

void
Test::testMergeSimple()
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

    EXPECT_EQUAL(docid, out.getDocId());
    EXPECT_EQUAL(8u, out.end() - out.begin());

    EXPECT_EQUAL( 5u, out.begin()[0].getPosition());
    EXPECT_EQUAL( 7u, out.begin()[1].getPosition());
    EXPECT_EQUAL(10u, out.begin()[2].getPosition());
    EXPECT_EQUAL(15u, out.begin()[3].getPosition());
    EXPECT_EQUAL(20u, out.begin()[4].getPosition());
    EXPECT_EQUAL(22u, out.begin()[5].getPosition());
    EXPECT_EQUAL(27u, out.begin()[6].getPosition());
    EXPECT_EQUAL(28u, out.begin()[7].getPosition());

    EXPECT_EQUAL(0.25, out.begin()[0].getMatchExactness());
    EXPECT_EQUAL( 0.5, out.begin()[1].getMatchExactness());
    EXPECT_EQUAL( 1.5, out.begin()[2].getMatchExactness());
    EXPECT_EQUAL( 1.0, out.begin()[3].getMatchExactness());
    EXPECT_EQUAL( 4.0, out.begin()[4].getMatchExactness());
    EXPECT_EQUAL(0.75, out.begin()[5].getMatchExactness());
    EXPECT_EQUAL( 3.0, out.begin()[6].getMatchExactness());
    EXPECT_EQUAL( 7.5, out.begin()[7].getMatchExactness());

    // one stale input

    docid = 10;
    a.reset(docid);
    a.appendPosition(make_pos(5));
    a.appendPosition(make_pos(10));
    a.appendPosition(make_pos(15));

    merger.merge(docid);

    EXPECT_EQUAL(docid, out.getDocId());
    EXPECT_EQUAL(3u, out.end() - out.begin());

    EXPECT_EQUAL( 5u, out.begin()[0].getPosition());
    EXPECT_EQUAL(10u, out.begin()[1].getPosition());
    EXPECT_EQUAL(15u, out.begin()[2].getPosition());

    // both inputs are stale

    docid = 15;

    merger.merge(docid);
    EXPECT_NOT_EQUAL(docid, out.getDocId());
}


void
Test::testMergeMultifield()
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

    EXPECT_EQUAL(docid, out1.getDocId());
    EXPECT_EQUAL(docid, out2.getDocId());
    EXPECT_NOT_EQUAL(docid, out3.getDocId());

    EXPECT_EQUAL(2u, out1.end() - out1.begin());
    EXPECT_EQUAL(3u, out2.end() - out2.begin());

    EXPECT_EQUAL( 5u, out1.begin()[0].getPosition());
    EXPECT_EQUAL(15u, out1.begin()[1].getPosition());

    EXPECT_EQUAL( 5u, out2.begin()[0].getPosition());
    EXPECT_EQUAL( 7u, out2.begin()[1].getPosition());
    EXPECT_EQUAL(20u, out2.begin()[2].getPosition());

    EXPECT_EQUAL(1.0, out1.begin()[0].getMatchExactness());
    EXPECT_EQUAL(1.0, out1.begin()[1].getMatchExactness());

    EXPECT_EQUAL(1.5, out2.begin()[0].getMatchExactness());
    EXPECT_EQUAL(0.5, out2.begin()[1].getMatchExactness());
    EXPECT_EQUAL(1.5, out2.begin()[2].getMatchExactness());
}

void
Test::testMergeDuplicates()
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

    EXPECT_EQUAL(docid, out.getDocId());
    EXPECT_EQUAL(5u, out.end() - out.begin());

    EXPECT_EQUAL( 3u, out.begin()[0].getPosition());
    EXPECT_EQUAL(1.5, out.begin()[0].getMatchExactness());
    EXPECT_EQUAL( 5u, out.begin()[1].getPosition());
    EXPECT_EQUAL(0.5, out.begin()[1].getMatchExactness());
    EXPECT_EQUAL(10u, out.begin()[2].getPosition());
    EXPECT_EQUAL(1.5, out.begin()[2].getMatchExactness());
    EXPECT_EQUAL(15u, out.begin()[3].getPosition());
    EXPECT_EQUAL(1.5, out.begin()[3].getMatchExactness());
    EXPECT_EQUAL(17u, out.begin()[4].getPosition());
    EXPECT_EQUAL(1.5, out.begin()[4].getMatchExactness());
}

void
Test::testMergeFieldLength()
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

    EXPECT_EQUAL(docid, out.getDocId());
    EXPECT_EQUAL(1000u, out.getIterator().getFieldLength());
}

int
Test::Main()
{
    TEST_INIT("termmatchdatamerger_test");
    testMergeEmptyInput();
    testMergeSimple();
    testMergeMultifield();
    testMergeDuplicates();
    testMergeFieldLength();
    TEST_DONE();
}

TEST_APPHOOK(Test);
