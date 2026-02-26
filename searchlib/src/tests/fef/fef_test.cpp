// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/filter_threshold.h>
#include <vespa/searchlib/fef/objectstore.h>
#include <vespa/searchlib/queryeval/element_id_extractor.h>

#include <vespa/log/log.h>
LOG_SETUP("fef_test");

using namespace search::fef;
using search::queryeval::ElementIdExtractor;
using std::shared_ptr;
using search::feature_t;

namespace {

constexpr uint32_t docid3 = 3;

std::vector<uint32_t> elems(std::vector<uint32_t> element_ids) {
    return element_ids;
}

void set_elems(TermFieldMatchData& tfmd, uint32_t docid, const std::vector<uint32_t>& element_ids) {
    tfmd.reset(docid);
    for (auto element_id: element_ids) {
        tfmd.appendPosition({element_id, 0, 1, 1});
    }
    tfmd.setNumOccs(element_ids.size());
    tfmd.setFieldLength(100);
}

std::vector<uint32_t> get_elems(TermFieldMatchData& tfmd, uint32_t docid) {
    std::vector<uint32_t> element_ids;
    ElementIdExtractor::get_element_ids(tfmd, docid, element_ids);
    return element_ids;
}

void filter_elems(TermFieldMatchData& tfmd, uint32_t docid, const std::vector<uint32_t>& element_ids) {
    tfmd.filter_elements(docid, { element_ids.data(), element_ids.size() });
}

}

TEST(FefTest, test_layout)
{
    {
        TermFieldMatchData tmd;
        EXPECT_EQ(IllegalFieldId, tmd.getFieldId());
        EXPECT_TRUE(tmd.has_invalid_docid());
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

TEST(FefTest, term_field_match_data_filter_elements_normal)
{
    TermFieldMatchData tfmd;
    set_elems(tfmd, docid3, {1, 3, 5, 7, 9});
    EXPECT_EQ(elems({1, 3, 5, 7, 9}), get_elems(tfmd, docid3));
    EXPECT_EQ(5, tfmd.getNumOccs());
    filter_elems(tfmd, docid3, {1, 2, 3, 7, 8, 9, 10});
    EXPECT_EQ(elems({1, 3, 7, 9}), get_elems(tfmd, docid3));
    EXPECT_EQ(4, tfmd.getNumOccs());
    filter_elems(tfmd, docid3, {1, 2, 3});
    EXPECT_EQ(elems({1, 3}), get_elems(tfmd, docid3));
    EXPECT_EQ(2, tfmd.getNumOccs());
    filter_elems(tfmd, docid3, {2, 3});
    EXPECT_EQ(elems({3}), get_elems(tfmd, docid3));
    EXPECT_EQ(1, tfmd.getNumOccs());
    EXPECT_TRUE(tfmd.has_ranking_data(docid3));
    filter_elems(tfmd, docid3, {1, 2});
    EXPECT_EQ(elems({}), get_elems(tfmd, docid3));
    EXPECT_TRUE(tfmd.has_invalid_docid());
}

TEST(FefTest, term_field_match_data_filter_elements_future_match_data)
{
    TermFieldMatchData tfmd;
    constexpr uint32_t docid2 = 2;
    set_elems(tfmd, docid3, {1, 3});
    filter_elems(tfmd, docid2, {});
    EXPECT_EQ(elems({1, 3}), get_elems(tfmd, docid3));
    EXPECT_TRUE(tfmd.has_ranking_data(docid3));
}

TEST(FefTest, term_field_match_data_filter_elements_past_match_data)
{
    TermFieldMatchData tfmd;
    constexpr uint32_t docid4 = 4;
    set_elems(tfmd, docid3, {1, 3});
    filter_elems(tfmd, docid4, {1, 2, 3});
    EXPECT_EQ(elems({}), get_elems(tfmd, docid3));
    EXPECT_TRUE(tfmd.has_invalid_docid());
}

TEST(FefTest, term_field_match_data_filter_elements_empty_filter)
{
    TermFieldMatchData tfmd;
    set_elems(tfmd, docid3, {1, 3});
    filter_elems(tfmd, docid3, {});
    EXPECT_EQ(elems({}), get_elems(tfmd, docid3));
    EXPECT_TRUE(tfmd.has_invalid_docid());
}

TEST(FefTest, term_field_match_data_filter_elements_empty_match_data)
{
    TermFieldMatchData tfmd;
    set_elems(tfmd, docid3, {});
    EXPECT_TRUE(tfmd.has_ranking_data(docid3));
    filter_elems(tfmd, docid3, {1, 2, 3}); // Clear empty (before and after filtering) match data
    EXPECT_EQ(elems({}), get_elems(tfmd, docid3));
    EXPECT_TRUE(tfmd.has_invalid_docid());
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
