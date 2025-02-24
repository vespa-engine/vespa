// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/matching/partial_result.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/box.h>
#include <sstream>

using proton::matching::PartialResult;
using namespace vespalib;

void checkMerge(const std::vector<double> &a, const std::vector<double> &b,
                size_t maxHits, const std::vector<double> &expect)
{
    std::ostringstream os;
    os << "checkMerge " << ::testing::PrintToString(a) << ", " << ::testing::PrintToString(b) << ", " << maxHits;
    SCOPED_TRACE(os.str());
    PartialResult res_a(maxHits, false);
    PartialResult res_b(maxHits, false);
    for (size_t i = 0; i < a.size(); ++i) {
        res_a.add(search::RankedHit(i, a[i]));
    }
    res_a.totalHits(a.size());
    for (size_t i = 0; i < b.size(); ++i) {
        res_b.add(search::RankedHit(i, b[i]));
    }
    res_b.totalHits(b.size());
    res_a.merge(res_b);
    EXPECT_EQ(a.size() + b.size(), res_a.totalHits());
    ASSERT_EQ(expect.size(), res_a.size());
    for (size_t i = 0; i < expect.size(); ++i) {
        EXPECT_EQ(expect[i], res_a.hit(i).getRank());
    }
}

void checkMerge(const std::vector<std::string> &a, const std::vector<std::string> &b,
                size_t maxHits, const std::vector<std::string> &expect)
{
    std::ostringstream os;
    os << "checkMerge " << ::testing::PrintToString(a) << ", " << ::testing::PrintToString(b) << ", " << maxHits;
    SCOPED_TRACE(os.str());
    size_t len = 0;
    PartialResult res_a(maxHits, true);
    PartialResult res_b(maxHits, true);
    len = 0;
    for (size_t i = 0; i < a.size(); ++i) {
        len += a[i].size();
        res_a.add(search::RankedHit(i, 0.0), PartialResult::SortRef(a[i].data(), a[i].size()));
    }
    res_a.totalHits(a.size());
    EXPECT_EQ(len, res_a.sortDataSize());
    len = 0;
    for (size_t i = 0; i < b.size(); ++i) {
        len += b[i].size();
        res_b.add(search::RankedHit(i, 0.0), PartialResult::SortRef(b[i].data(), b[i].size()));
    }
    res_b.totalHits(b.size());
    EXPECT_EQ(len, res_b.sortDataSize());
    res_a.merge(res_b);
    EXPECT_EQ(a.size() + b.size(), res_a.totalHits());
    ASSERT_EQ(expect.size(), res_a.size());
    len = 0;
    for (size_t i = 0; i < expect.size(); ++i) {
        len += expect[i].size();
        EXPECT_EQ(expect[i], std::string(res_a.sortData(i).first, res_a.sortData(i).second));
    }
    EXPECT_EQ(len, res_a.sortDataSize());
}

TEST(PartialResultTest, require_that_partial_results_can_be_created_without_sort_data) {
    PartialResult res(100, false);
    EXPECT_EQ(0u, res.size());
    EXPECT_EQ(100u, res.maxSize());
    EXPECT_EQ(0u, res.totalHits());
    EXPECT_FALSE(res.hasSortData());
    EXPECT_EQ(0u, res.sortDataSize());
    res.add(search::RankedHit(1, 10.0));
    res.add(search::RankedHit(2, 5.0));
    res.totalHits(1000);
    EXPECT_EQ(1000u, res.totalHits());
    ASSERT_EQ(2u, res.size());
    EXPECT_EQ(1u, res.hit(0).getDocId());
    EXPECT_EQ(10.0, res.hit(0).getRank());
    EXPECT_EQ(2u, res.hit(1).getDocId());
    EXPECT_EQ(5.0, res.hit(1).getRank());
}

TEST(PartialResultTest, require_that_partial_results_can_be_created_with_sort_data) {
    std::string str1("aaa");
    std::string str2("bbb");
    PartialResult res(100, true);
    EXPECT_EQ(0u, res.size());
    EXPECT_EQ(100u, res.maxSize());
    EXPECT_EQ(0u, res.totalHits());
    EXPECT_TRUE(res.hasSortData());
    EXPECT_EQ(0u, res.sortDataSize());
    res.add(search::RankedHit(1, 10.0), PartialResult::SortRef(str1.data(), str1.size()));
    res.add(search::RankedHit(2, 5.0), PartialResult::SortRef(str2.data(), str2.size()));
    res.totalHits(1000);
    EXPECT_EQ(1000u, res.totalHits());
    ASSERT_EQ(2u, res.size());
    EXPECT_EQ(1u, res.hit(0).getDocId());
    EXPECT_EQ(10.0, res.hit(0).getRank());
    EXPECT_EQ(str1.data(), res.sortData(0).first);
    EXPECT_EQ(str1.size(), res.sortData(0).second);
    EXPECT_EQ(2u, res.hit(1).getDocId());
    EXPECT_EQ(5.0, res.hit(1).getRank());
    EXPECT_EQ(str2.data(), res.sortData(1).first);
    EXPECT_EQ(str2.size(), res.sortData(1).second);
}

TEST(PartialResultTest, require_that_partial_results_without_sort_data_are_merged_correctly) {
    checkMerge(make_box(5.0, 4.0, 3.0), make_box(4.5, 3.5), 3, make_box(5.0, 4.5, 4.0));
    checkMerge(make_box(4.5, 3.5), make_box(5.0, 4.0, 3.0), 3, make_box(5.0, 4.5, 4.0));
    checkMerge(make_box(1.0), make_box(2.0), 10, make_box(2.0, 1.0));
    checkMerge(make_box(2.0), make_box(1.0), 10, make_box(2.0, 1.0));
    checkMerge(std::vector<double>(), make_box(1.0), 10, make_box(1.0));
    checkMerge(make_box(1.0), std::vector<double>(), 10, make_box(1.0));
    checkMerge(std::vector<double>(), make_box(1.0), 0, std::vector<double>());
    checkMerge(make_box(1.0), std::vector<double>(), 0, std::vector<double>());
    checkMerge(std::vector<double>(), std::vector<double>(), 10, std::vector<double>());
}

TEST(PartialResultTest, require_that_partial_results_with_sort_data_are_merged_correctly) {
    checkMerge(make_box<std::string>("a", "c", "e"), make_box<std::string>("b", "d"), 3, make_box<std::string>("a", "b", "c"));
    checkMerge(make_box<std::string>("b", "d"), make_box<std::string>("a", "c", "e"), 3, make_box<std::string>("a", "b", "c"));
    checkMerge(make_box<std::string>("a"), make_box<std::string>("aa"), 10, make_box<std::string>("a", "aa"));
    checkMerge(make_box<std::string>("aa"), make_box<std::string>("a"), 10, make_box<std::string>("a", "aa"));
    checkMerge(std::vector<std::string>(), make_box<std::string>("a"), 10, make_box<std::string>("a"));
    checkMerge(make_box<std::string>("a"), std::vector<std::string>(), 10, make_box<std::string>("a"));
    checkMerge(std::vector<std::string>(), make_box<std::string>("a"), 0, std::vector<std::string>());
    checkMerge(make_box<std::string>("a"), std::vector<std::string>(), 0, std::vector<std::string>());
    checkMerge(std::vector<std::string>(), std::vector<std::string>(), 10, std::vector<std::string>());
}

TEST(PartialResultTest, require_that_lower_docid_is_preferred_when_sorting_on_rank) {
    PartialResult res_a(1, false);
    PartialResult res_b(1, false);
    PartialResult res_c(1, false);
    res_a.add(search::RankedHit(2, 1.0));
    res_b.add(search::RankedHit(3, 1.0));
    res_c.add(search::RankedHit(1, 1.0));
    res_a.merge(res_b);
    ASSERT_EQ(1u, res_a.size());
    EXPECT_EQ(2u, res_a.hit(0).getDocId());
    res_a.merge(res_c);
    ASSERT_EQ(1u, res_a.size());
    EXPECT_EQ(1u, res_a.hit(0).getDocId());
}

TEST(PartialResultTest, require_that_lower_docid_is_preferred_when_using_sortspec) {
    std::string foo("foo");
    PartialResult res_a(1, true);
    PartialResult res_b(1, true);
    PartialResult res_c(1, true);
    res_a.add(search::RankedHit(2, 1.0), PartialResult::SortRef(foo.data(), foo.size()));
    res_b.add(search::RankedHit(3, 1.0), PartialResult::SortRef(foo.data(), foo.size()));
    res_c.add(search::RankedHit(1, 1.0), PartialResult::SortRef(foo.data(), foo.size()));
    res_a.merge(res_b);
    ASSERT_EQ(1u, res_a.size());
    EXPECT_EQ(2u, res_a.hit(0).getDocId());
    res_a.merge(res_c);
    ASSERT_EQ(1u, res_a.size());
    EXPECT_EQ(1u, res_a.hit(0).getDocId());
}

GTEST_MAIN_RUN_ALL_TESTS()
