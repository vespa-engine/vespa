// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/filter_threshold.h>
#include <vespa/searchlib/fef/objectstore.h>

#include <vespa/log/log.h>
LOG_SETUP("fef_test");

using namespace search::fef;
using std::shared_ptr;
using search::feature_t;

TEST(FefTest, test_layout)
{
    {
        TermFieldMatchData tmd;
        EXPECT_EQ(IllegalFieldId, tmd.getFieldId());
        EXPECT_EQ(TermFieldMatchData::invalidId(), tmd.getDocId());
    }
    MatchDataLayout mdl;
    EXPECT_EQ(mdl.allocTermField(0), 0u);
    EXPECT_EQ(mdl.allocTermField(42), 1u);
    EXPECT_EQ(mdl.allocTermField(IllegalFieldId), 2u);

    MatchData::UP md = mdl.createMatchData();
    EXPECT_EQ(md->getNumTermFields(), 3u);
    TermFieldMatchData *t0 = md->resolveTermField(0);
    TermFieldMatchData *t1 = md->resolveTermField(1);
    TermFieldMatchData *t2 = md->resolveTermField(2);
    EXPECT_EQ(t1, t0 + 1);
    EXPECT_EQ(t2, t1 + 1);
    EXPECT_EQ(0u, t0->getFieldId());
    EXPECT_EQ(42u, t1->getFieldId());
    EXPECT_EQ(IllegalFieldId, t2->getFieldId());
}

TEST(FefTest, test_ObjectStore)
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
    EXPECT_EQ(o1, s.get("a"));
    EXPECT_TRUE(nullptr == s.get("b"));
    s.add("b", std::move(u2));
    EXPECT_EQ(o1, s.get("a"));
    EXPECT_EQ(o2, s.get("b"));
    s.add("a", std::move(u11));
    EXPECT_EQ(o11, s.get("a"));
}

TEST(FefTest, test_TermFieldMatchDataAppend)
{
    TermFieldMatchData tmd;
    EXPECT_EQ(0u, tmd.size());
    EXPECT_EQ(1u, tmd.capacity());
    TermFieldMatchDataPosition pos;
    tmd.appendPosition(pos);
    EXPECT_EQ(1u, tmd.size());
    EXPECT_EQ(1u, tmd.capacity());
    tmd.appendPosition(pos);
    EXPECT_EQ(2u, tmd.size());
    EXPECT_EQ(42u, tmd.capacity());
    uint32_t resizeCount(0);
    const TermFieldMatchDataPosition * prev = tmd.begin();
    for (size_t i(2); i < std::numeric_limits<uint16_t>::max(); i++) {
        EXPECT_EQ(i, tmd.size());
        tmd.appendPosition(pos);
        const TermFieldMatchDataPosition * cur = tmd.begin();
        if (cur != prev) {
            prev = cur;
            resizeCount++;
        }
    }
    EXPECT_EQ(11u, resizeCount);
    EXPECT_EQ(std::numeric_limits<uint16_t>::max(), tmd.size());
    EXPECT_EQ(std::numeric_limits<uint16_t>::max(), tmd.capacity());
    for (size_t i(0); i < 10; i++) {
        tmd.appendPosition(pos);
        EXPECT_EQ(prev, tmd.begin());
        EXPECT_EQ(std::numeric_limits<uint16_t>::max(), tmd.size());
        EXPECT_EQ(std::numeric_limits<uint16_t>::max(), tmd.capacity());
    }
}

TEST(FefTest, verify_size_of_essential_fef_classes) {
    EXPECT_EQ(16u,sizeof(TermFieldMatchData::Positions));
    EXPECT_EQ(24u,sizeof(TermFieldMatchDataPosition));
    EXPECT_EQ(24u,sizeof(TermFieldMatchData::Features));
    EXPECT_EQ(40u,sizeof(TermFieldMatchData));
    EXPECT_EQ(48u, sizeof(search::fef::FeatureExecutor));
}

TEST(FefTest, FilterThreshold_can_represent_a_boolean_is_filter_value)
{
    FilterThreshold a;
    EXPECT_FALSE(a.is_filter());

    FilterThreshold b(false);
    EXPECT_FALSE(b.is_filter());

    FilterThreshold c(true);
    EXPECT_TRUE(c.is_filter());
}

TEST(FefTest, FilterThreshold_can_represent_a_threshold_value)
{
    FilterThreshold a;
    EXPECT_FALSE(a.is_filter(1.0));

    FilterThreshold b(0.5);
    EXPECT_EQ((float)0.5, b.threshold());
    EXPECT_FALSE(b.is_filter());
    EXPECT_FALSE(b.is_filter(0.5));
    EXPECT_TRUE(b.is_filter(0.51));
}

GTEST_MAIN_RUN_ALL_TESTS()
