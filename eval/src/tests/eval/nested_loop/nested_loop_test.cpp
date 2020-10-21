// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/nested_loop.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;

std::vector<size_t> run_single(size_t idx_in, const std::vector<size_t> &loop, const std::vector<size_t> &stride) {
    std::vector<size_t> result;
    auto capture = [&](size_t idx_out) { result.push_back(idx_out); };
    assert(loop.size() == stride.size());
    run_nested_loop(idx_in, loop, stride, capture);
    return result;
}

std::vector<std::pair<size_t,size_t>> run_double(size_t idx1_in, size_t idx2_in, const std::vector<size_t> &loop,
                                                 const std::vector<size_t> &stride1, const std::vector<size_t> &stride2)
{
    std::vector<std::pair<size_t,size_t>> result;
    auto capture = [&](size_t idx1_out, size_t idx2_out) { result.emplace_back(idx1_out, idx2_out); };
    assert(loop.size() == stride1.size());
    assert(loop.size() == stride2.size());
    run_nested_loop(idx1_in, idx2_in, loop, stride1, stride2, capture);
    return result;
}

void verify_double(size_t idx1_in, size_t idx2_in, const std::vector<size_t> &loop,
                   const std::vector<size_t> &stride1, const std::vector<size_t> &stride2)
{
    auto res1 = run_single(idx1_in, loop, stride1);
    auto res2 = run_single(idx2_in, loop, stride2);
    ASSERT_EQ(res1.size(), res2.size());
    std::vector<std::pair<size_t,size_t>> expect;
    for (size_t i = 0; i < res1.size(); ++i) {
        expect.emplace_back(res1[i], res2[i]);
    }
    auto actual = run_double(idx1_in, idx2_in, loop, stride1, stride2);
    EXPECT_EQ(actual, expect);
}

std::vector<size_t> v(std::vector<size_t> vec) { return vec; }

TEST(NestedLoopTest, single_nested_loop_can_be_executed) {
    EXPECT_EQ(v({123}), run_single(123, {}, {}));
    EXPECT_EQ(v({10,11}), run_single(10, {2}, {1}));
    EXPECT_EQ(v({100,110,101,111}), run_single(100, {2,2}, {1,10}));
    EXPECT_EQ(v({100,110,100,110,101,111,101,111}), run_single(100, {2,2,2}, {1,0,10}));
    EXPECT_EQ(v({100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115}),
              run_single(100, {2,2,2,2}, {8,4,2,1}));
}

TEST(NestedLoopTest, double_nested_loop_can_be_executed) {
    verify_double(10, 20, {}, {}, {});
    verify_double(10, 20, {3}, {5}, {7});
    verify_double(10, 20, {3,3}, {2,3}, {7,5});
    verify_double(10, 20, {3,3,2}, {2,0,3}, {0,7,5});
    verify_double(10, 20, {2,3,2,3}, {7,2,1,3}, {3,7,5,1});
}

GTEST_MAIN_RUN_ALL_TESTS()
