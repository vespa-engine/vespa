// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/compact_buffer_candidates.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::datastore::CompactBufferCandidates;

namespace {

constexpr uint32_t num_buffers = 1024;
constexpr double default_ratio = 0.2 / 2;
constexpr size_t default_slack = 1000;

};


class CompactBufferCandidatesTest : public ::testing::Test
{
public:
    CompactBufferCandidates candidates;
    CompactBufferCandidatesTest();
    ~CompactBufferCandidatesTest() override;
    void reset_candidates(uint32_t max_buffers);
    CompactBufferCandidatesTest& add(uint32_t buffer_id, size_t used, size_t dead);
    void assert_select(const std::vector<uint32_t>& exp);
};

CompactBufferCandidatesTest::CompactBufferCandidatesTest()
    : ::testing::Test(),
      candidates(num_buffers, 1, default_ratio, default_slack)
{
}

CompactBufferCandidatesTest::~CompactBufferCandidatesTest() = default;

void
CompactBufferCandidatesTest::reset_candidates(uint32_t max_buffers)
{
    candidates = CompactBufferCandidates(num_buffers, max_buffers, default_ratio, default_slack);
}

CompactBufferCandidatesTest&
CompactBufferCandidatesTest::add(uint32_t buffer_id, size_t used, size_t dead)
{
    candidates.add(buffer_id, used, dead);
    return *this;
}

void
CompactBufferCandidatesTest::assert_select(const std::vector<uint32_t>& exp)
{
    std::vector<uint32_t> act;
    candidates.select(act);
    EXPECT_EQ(exp, act);
}

TEST_F(CompactBufferCandidatesTest, select_single)
{
    add(0, 10000, 2000).add(1, 10000, 3000);
    assert_select({1});
}

TEST_F(CompactBufferCandidatesTest, select_two)
{
    reset_candidates(2);
    add(0, 10000, 2000).add(3, 10000, 3000).add(7, 10000, 4000);
    assert_select({7, 3});
}

TEST_F(CompactBufferCandidatesTest, select_all)
{
    reset_candidates(4);
    add(1, 10000, 2000).add(3, 10000, 4000).add(8, 10000, 3000);
    assert_select({3, 8, 1});
}

TEST_F(CompactBufferCandidatesTest, select_cutoff_by_ratio)
{
    reset_candidates(4);
    add(1, 100000, 9999).add(3, 100000, 40000).add(8, 100000, 30000);
    assert_select({3, 8});
}

TEST_F(CompactBufferCandidatesTest, select_cutoff_by_slack)
{
    reset_candidates(4);
    add(1, 2000, 999).add(3, 2000, 1200).add(9, 2000, 1300);
    assert_select({9, 3});
}

GTEST_MAIN_RUN_ALL_TESTS()
