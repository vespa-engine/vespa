// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/nested_loop.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;

std::vector<size_t> run_loop(size_t idx_in, const std::vector<size_t> &loop, const std::vector<size_t> &stride) {
    std::vector<size_t> result;
    auto capture = [&](size_t idx_out) { result.push_back(idx_out); };
    assert(loop.size() == stride.size());
    run_nested_loop(idx_in, loop, stride, capture);
    return result;
}

std::vector<std::pair<size_t,size_t>> run_two_loops(size_t idx1_in, size_t idx2_in, const std::vector<size_t> &loop,
                                                    const std::vector<size_t> &stride1, const std::vector<size_t> &stride2)
{
    std::vector<std::pair<size_t,size_t>> result;
    auto capture = [&](size_t idx1_out, size_t idx2_out) { result.emplace_back(idx1_out, idx2_out); };
    assert(loop.size() == stride1.size());
    assert(loop.size() == stride2.size());
    run_nested_loop(idx1_in, idx2_in, loop, stride1, stride2, capture);
    return result;
}

void add_entry(std::vector<std::vector<size_t>> &result, std::vector<size_t> value) {
    result.push_back(std::move(value));
}

std::vector<std::vector<size_t>> run_three_loops(size_t idx1_in, size_t idx2_in, size_t idx3_in, const std::vector<size_t> &loop,
                                                 const std::vector<size_t> &stride1, const std::vector<size_t> &stride2, const std::vector<size_t> &stride3)
{
    std::vector<std::vector<size_t>> result;
    auto capture = [&](size_t idx1_out, size_t idx2_out, size_t idx3_out) { add_entry(result, {idx1_out, idx2_out, idx3_out}); };
    assert(loop.size() == stride1.size());
    assert(loop.size() == stride2.size());
    assert(loop.size() == stride3.size());
    run_nested_loop(idx1_in, idx2_in, idx3_in, loop, stride1, stride2, stride3, capture);
    return result;
}

void verify_two(size_t idx1_in, size_t idx2_in, const std::vector<size_t> &loop,
                const std::vector<size_t> &stride1, const std::vector<size_t> &stride2)
{
    auto res1 = run_loop(idx1_in, loop, stride1);
    auto res2 = run_loop(idx2_in, loop, stride2);
    ASSERT_EQ(res1.size(), res2.size());
    std::vector<std::pair<size_t,size_t>> expect;
    for (size_t i = 0; i < res1.size(); ++i) {
        expect.emplace_back(res1[i], res2[i]);
    }
    auto actual = run_two_loops(idx1_in, idx2_in, loop, stride1, stride2);
    EXPECT_EQ(actual, expect);
}

void verify_three(size_t idx1_in, size_t idx2_in, size_t idx3_in, const std::vector<size_t> &loop,
                  const std::vector<size_t> &stride1, const std::vector<size_t> &stride2, const std::vector<size_t> &stride3)
{
    auto res1 = run_loop(idx1_in, loop, stride1);
    auto res2 = run_loop(idx2_in, loop, stride2);
    auto res3 = run_loop(idx3_in, loop, stride3);
    ASSERT_EQ(res1.size(), res2.size());
    ASSERT_EQ(res1.size(), res3.size());
    std::vector<std::vector<size_t>> expect;
    for (size_t i = 0; i < res1.size(); ++i) {
        add_entry(expect, {res1[i], res2[i], res3[i]});
    }
    auto actual = run_three_loops(idx1_in, idx2_in, idx3_in, loop, stride1, stride2, stride3);
    EXPECT_EQ(actual, expect);
}

std::vector<size_t> v(std::vector<size_t> vec) { return vec; }

TEST(NestedLoopTest, nested_loop_can_be_executed) {
    EXPECT_EQ(v({123}), run_loop(123, {}, {}));
    EXPECT_EQ(v({10,11}), run_loop(10, {2}, {1}));
    EXPECT_EQ(v({100,110,101,111}), run_loop(100, {2,2}, {1,10}));
    EXPECT_EQ(v({100,110,100,110,101,111,101,111}), run_loop(100, {2,2,2}, {1,0,10}));
    EXPECT_EQ(v({100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115}),
              run_loop(100, {2,2,2,2}, {8,4,2,1}));
}

TEST(NestedLoopTest, two_parallel_nested_loops_can_be_executed) {
    verify_two(10, 20, {}, {}, {});
    verify_two(10, 20, {3}, {5}, {7});
    verify_two(10, 20, {3,3}, {2,3}, {7,5});
    verify_two(10, 20, {3,3,2}, {2,0,3}, {0,7,5});
    verify_two(10, 20, {2,3,2,3}, {7,2,1,3}, {3,7,5,1});
}

TEST(NestedLoopTest, three_parallel_nested_loops_can_be_executed) {
    verify_three(10, 20, 30, {}, {}, {}, {});
    verify_three(10, 20, 30, {3}, {5}, {7}, {3});
    verify_three(10, 20, 30, {3,3}, {2,3}, {7,5}, {5, 3});
    verify_three(10, 20, 30, {3,3,2}, {2,0,3}, {0,7,5}, {5, 3, 0});
    verify_three(10, 20, 30, {2,3,2,3}, {7,2,1,3}, {3,7,5,1}, {1,5,7,3});
}

GTEST_MAIN_RUN_ALL_TESTS()
