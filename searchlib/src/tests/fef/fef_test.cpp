// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("fef_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/objectstore.h>

using namespace search::fef;
using std::shared_ptr;
using search::feature_t;

class Test : public vespalib::TestApp
{
public:
    void testLayout();
    void testObjectStore();
    void testTermFieldMatchDataAppend();
    int Main() override;
};

void
Test::testLayout()
{
    {
        TermFieldMatchData tmd;
        EXPECT_EQUAL(IllegalFieldId, tmd.getFieldId());
        EXPECT_EQUAL(TermFieldMatchData::invalidId(), tmd.getDocId());
    }
    MatchDataLayout mdl;
    EXPECT_EQUAL(mdl.allocTermField(0), 0u);
    EXPECT_EQUAL(mdl.allocTermField(42), 1u);
    EXPECT_EQUAL(mdl.allocTermField(IllegalFieldId), 2u);

    MatchData::UP md = mdl.createMatchData();
    EXPECT_EQUAL(md->getNumTermFields(), 3u);
    TermFieldMatchData *t0 = md->resolveTermField(0);
    TermFieldMatchData *t1 = md->resolveTermField(1);
    TermFieldMatchData *t2 = md->resolveTermField(2);
    EXPECT_EQUAL(t1, t0 + 1);
    EXPECT_EQUAL(t2, t1 + 1);
    EXPECT_EQUAL(0u, t0->getFieldId());
    EXPECT_EQUAL(42u, t1->getFieldId());
    EXPECT_EQUAL(IllegalFieldId, t2->getFieldId());
}

void
Test::testObjectStore()
{
    ObjectStore s;
    class Object : public Anything {
    };
    Anything::UP u1(new Object());
    Anything::UP u11(new Object());
    Anything::UP u2(new Object());
    const Anything * o1(u1.get());
    const Anything * o11(u11.get());
    const Anything * o2(u2.get());
    EXPECT_TRUE(nullptr == s.get("a"));
    s.add("a", std::move(u1));
    EXPECT_EQUAL(o1, s.get("a"));
    EXPECT_TRUE(nullptr == s.get("b"));
    s.add("b", std::move(u2));
    EXPECT_EQUAL(o1, s.get("a"));
    EXPECT_EQUAL(o2, s.get("b"));
    s.add("a", std::move(u11));
    EXPECT_EQUAL(o11, s.get("a"));
}

void
Test::testTermFieldMatchDataAppend()
{
    TermFieldMatchData tmd;
    EXPECT_EQUAL(0u, tmd.size());
    EXPECT_EQUAL(1u, tmd.capacity());
    TermFieldMatchDataPosition pos;
    tmd.appendPosition(pos);
    EXPECT_EQUAL(1u, tmd.size());
    EXPECT_EQUAL(1u, tmd.capacity());
    tmd.appendPosition(pos);
    EXPECT_EQUAL(2u, tmd.size());
    EXPECT_EQUAL(2u, tmd.capacity());
    for (size_t i(2); i < std::numeric_limits<uint16_t>::max(); i++) {
        EXPECT_EQUAL(i, tmd.size());
        EXPECT_EQUAL(std::min(size_t(std::numeric_limits<uint16_t>::max()), vespalib::roundUp2inN(i)), tmd.capacity());
        tmd.appendPosition(pos);
    }
    EXPECT_EQUAL(std::numeric_limits<uint16_t>::max(), tmd.size());
    EXPECT_EQUAL(std::numeric_limits<uint16_t>::max(), tmd.capacity());
    tmd.appendPosition(pos);
    EXPECT_EQUAL(std::numeric_limits<uint16_t>::max(), tmd.size());
    EXPECT_EQUAL(std::numeric_limits<uint16_t>::max(), tmd.capacity());
}

int
Test::Main()
{
    TEST_INIT("fef_test");
    testLayout();
    testObjectStore();
    testTermFieldMatchDataAppend();
    TEST_DONE();
}

TEST_APPHOOK(Test);
