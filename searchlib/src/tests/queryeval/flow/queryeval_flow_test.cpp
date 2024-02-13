// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/flow.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>
#include <random>

constexpr size_t loop_cnt = 64;

using namespace search::queryeval;

template <typename FLOW>
double ordered_cost_of(const std::vector<FlowStats> &data, bool strict) {
    return flow::ordered_cost_of(flow::DirectAdapter(), data, FLOW(strict));
}

std::vector<FlowStats> gen_data(size_t size) {
    static std::mt19937 gen;
    static std::uniform_real_distribution<double>    estimate(0.1,  0.9);
    static std::uniform_real_distribution<double>        cost(1.0, 10.0);
    static std::uniform_real_distribution<double> strict_cost(0.1,  5.0);
    std::vector<FlowStats> result;
    result.reserve(size);
    for (size_t i = 0; i < size; ++i) {
        result.emplace_back(estimate(gen), cost(gen), strict_cost(gen));
    }
    return result;
}

template <typename T, typename F>
void each_perm(std::vector<T> &data, size_t k, F fun) {
    if (k <= 1) {
        fun(const_cast<const std::vector<T> &>(data));
    } else {
        each_perm(data, k-1, fun);
        for (size_t i = 0; i < k-1; ++i) {
            if (k & 1) {
                std::swap(data[0], data[k-1]);
            } else {
                std::swap(data[i], data[k-1]);
            }
            each_perm(data, k-1, fun);
        }
    }
}

template <typename T, typename F>
void each_perm(std::vector<T> &data, F fun) {
    each_perm(data, data.size(), fun);
}

TEST(FlowTest, perm_test) {
    std::set<std::vector<int>> seen;
    std::vector<int> data = {1,2,3,4,5};
    auto hook = [&](const std::vector<int> &perm) {
                    EXPECT_EQ(perm.size(), 5);
                    seen.insert(perm);
                };
    each_perm(data, hook);
    EXPECT_EQ(seen.size(), 120);
}

template <template <typename> typename ORDER>
void verify_ordering_is_strict_weak() {
    auto cmp = ORDER(flow::DirectAdapter());
    auto input = gen_data(7);
    input.emplace_back(0.5, 1.5, 0.5);
    input.emplace_back(0.5, 1.5, 0.5);
    input.emplace_back(0.5, 1.5, 0.5);
    input.emplace_back(0.0, 1.5, 0.5);
    input.emplace_back(0.0, 1.5, 0.5);
    input.emplace_back(0.5, 0.0, 0.5);
    input.emplace_back(0.5, 0.0, 0.5);
    input.emplace_back(0.5, 1.5, 0.0);
    input.emplace_back(0.5, 1.5, 0.0);
    input.emplace_back(0.0, 0.0, 0.0);
    input.emplace_back(0.0, 0.0, 0.0);
    std::vector<FlowStats> output;
    for (const FlowStats &in: input) {
        EXPECT_FALSE(cmp(in, in)); // Irreflexivity
        size_t out_idx = 0;
        bool lower = false;
        bool upper = false;
        for (const FlowStats &out: output) {
            if (cmp(out, in)) {
                EXPECT_FALSE(cmp(in, out)); // Antisymmetry
                EXPECT_FALSE(lower); // Transitivity
                EXPECT_FALSE(upper); // Transitivity
                ++out_idx;
            } else {
                lower = true;
                if (cmp(in, out)) {
                    upper = true;
                } else {
                    EXPECT_FALSE(upper); // Transitivity
                }
            }
        }
        output.insert(output.begin() + out_idx, in);
    }
}

TEST(FlowTest, and_ordering_is_strict_weak) {
    verify_ordering_is_strict_weak<flow::MinAndCost>();
}

TEST(FlowTest, or_ordering_is_strict_weak) {
    verify_ordering_is_strict_weak<flow::MinOrCost>();
}

struct ExpectFlow {
    double flow;
    double est;
    bool strict;
};

void verify_flow(auto flow, const std::vector<double> &est_list, const std::vector<ExpectFlow> &expect) {
    ASSERT_EQ(est_list.size() + 1, expect.size());
    for (size_t i = 0; i < expect.size(); ++i) {
        EXPECT_DOUBLE_EQ(flow.flow(), expect[i].flow);
        EXPECT_DOUBLE_EQ(flow.estimate(), expect[i].est);
        EXPECT_EQ(flow.strict(), expect[i].strict);
        if (i < est_list.size()) {
            flow.add(est_list[i]);
        }
    }
}

TEST(FlowTest, full_and_flow) {
    for (bool strict: {false, true}) {
        verify_flow(AndFlow(strict), {0.4, 0.7, 0.2},
                    {{1.0, 0.0, strict},
                     {0.4, 0.4, false},
                     {0.4*0.7, 0.4*0.7, false},
                     {0.4*0.7*0.2, 0.4*0.7*0.2, false}});
    }
}

TEST(FlowTest, partial_and_flow) {
    for (double in: {1.0, 0.5, 0.25}) {
        verify_flow(AndFlow(in), {0.4, 0.7, 0.2},
                    {{in, 0.0, false},
                     {in*0.4, in*0.4, false},
                     {in*0.4*0.7, in*0.4*0.7, false},
                     {in*0.4*0.7*0.2, in*0.4*0.7*0.2, false}});
    }
}

TEST(FlowTest, full_or_flow) {
    verify_flow(OrFlow(false), {0.4, 0.7, 0.2},
                {{1.0, 0.0, false},
                 {0.6, 1.0-0.6, false},
                 {0.6*0.3, 1.0-0.6*0.3, false},
                 {0.6*0.3*0.8, 1.0-0.6*0.3*0.8, false}});
    verify_flow(OrFlow(true), {0.4, 0.7, 0.2},
                {{1.0, 0.0, true},
                 {1.0, 1.0-0.6, true},
                 {1.0, 1.0-0.6*0.3, true},
                 {1.0, 1.0-0.6*0.3*0.8, true}});
}

TEST(FlowTest, partial_or_flow) {
    for (double in: {1.0, 0.5, 0.25}) {
        verify_flow(OrFlow(in), {0.4, 0.7, 0.2},
                    {{in, 0.0, false},
                     {in*0.6, 1.0-in*0.6, false},
                     {in*0.6*0.3, 1.0-in*0.6*0.3, false},
                     {in*0.6*0.3*0.8, 1.0-in*0.6*0.3*0.8, false}});
    }
}

TEST(FlowTest, full_and_not_flow) {
    for (bool strict: {false, true}) {
        verify_flow(AndNotFlow(strict), {0.4, 0.7, 0.2},
                    {{1.0, 0.0, strict},
                     {0.4, 0.4, false},
                     {0.4*0.3, 0.4*0.3, false},
                     {0.4*0.3*0.8, 0.4*0.3*0.8, false}});
    }
}

TEST(FlowTest, partial_and_not_flow) {
    for (double in: {1.0, 0.5, 0.25}) {
        verify_flow(AndNotFlow(in), {0.4, 0.7, 0.2},
                    {{in, 0.0, false},
                     {in*0.4, in*0.4, false},
                     {in*0.4*0.3, in*0.4*0.3, false},
                     {in*0.4*0.3*0.8, in*0.4*0.3*0.8, false}});
    }
}

TEST(FlowTest, flow_cost) {
    std::vector<FlowStats> data = {{0.4, 1.1, 0.6}, {0.7, 1.2, 0.5}, {0.2, 1.3, 0.4}};
    EXPECT_DOUBLE_EQ(ordered_cost_of<AndFlow>(data, false), 1.1 + 0.4*1.2 + 0.4*0.7*1.3);
    EXPECT_DOUBLE_EQ(ordered_cost_of<AndFlow>(data, true), 0.6 + 0.4*1.2 + 0.4*0.7*1.3);
    EXPECT_DOUBLE_EQ(ordered_cost_of<OrFlow>(data, false), 1.1 + 0.6*1.2 + 0.6*0.3*1.3);
    EXPECT_DOUBLE_EQ(ordered_cost_of<OrFlow>(data, true), 0.6 + 0.5 + 0.4);
    EXPECT_DOUBLE_EQ(ordered_cost_of<AndNotFlow>(data, false), 1.1 + 0.4*1.2 + 0.4*0.3*1.3);
    EXPECT_DOUBLE_EQ(ordered_cost_of<AndNotFlow>(data, true), 0.6 + 0.4*1.2 + 0.4*0.3*1.3);
}

TEST(FlowTest, optimal_and_flow) {
    for (size_t i = 0; i < loop_cnt; ++i) {
        for (bool strict: {false, true}) {
            auto data = gen_data(7);
            double ref_est = AndFlow::estimate_of(data);
            double min_cost = AndFlow::cost_of(data, strict);
            double max_cost = 0.0;
            AndFlow::sort(data, strict);
            EXPECT_EQ(ordered_cost_of<AndFlow>(data, strict), min_cost);
            auto check = [&](const std::vector<FlowStats> &my_data) noexcept {
                             double my_cost = ordered_cost_of<AndFlow>(my_data, strict);
                             EXPECT_LE(min_cost, my_cost);
                             max_cost = std::max(max_cost, my_cost);
                         };
            each_perm(data, check);
            if (loop_cnt < 1024 || i % 1024 == 0) {
                fprintf(stderr, "  AND cost(%zu,%s): min: %g, max: %g, factor: %g\n",
                        i, strict ? "strict" : "non-strict", min_cost, max_cost, max_cost / min_cost);
            }
            EXPECT_NEAR(ref_est, AndFlow::estimate_of(data), 1e-9);
        }
    }
}

TEST(FlowTest, optimal_or_flow) {
    for (size_t i = 0; i < loop_cnt; ++i) {
        for (bool strict: {false, true}) {
            auto data = gen_data(7);
            double min_cost = OrFlow::cost_of(data, strict);
            double max_cost = 0.0;
            OrFlow::sort(data, strict);
            EXPECT_EQ(ordered_cost_of<OrFlow>(data, strict), min_cost);
            auto check = [&](const std::vector<FlowStats> &my_data) noexcept {
                             double my_cost = ordered_cost_of<OrFlow>(my_data, strict);
                             EXPECT_LE(min_cost, my_cost + 1e-9);
                             max_cost = std::max(max_cost, my_cost);
                         };
            each_perm(data, check);
            if (loop_cnt < 1024 || i % 1024 == 0) {
                fprintf(stderr, "  OR cost(%zu,%s): min: %g, max: %g, factor: %g\n",
                        i, strict ? "strict" : "non-strict", min_cost, max_cost, max_cost / min_cost);
            }
        }
    }
}

TEST(FlowTest, optimal_and_not_flow) {
    for (size_t i = 0; i < loop_cnt; ++i) {
        for (bool strict: {false, true}) {
            auto data = gen_data(7);
            FlowStats first = data[0];
            double min_cost = AndNotFlow::cost_of(data, strict);
            double max_cost = 0.0;
            AndNotFlow::sort(data, strict);
            EXPECT_EQ(data[0], first);
            EXPECT_EQ(ordered_cost_of<AndNotFlow>(data, strict), min_cost);
            auto check = [&](const std::vector<FlowStats> &my_data) noexcept {
                             if (my_data[0] == first) {
                                 double my_cost = ordered_cost_of<AndNotFlow>(my_data, strict);
                                 EXPECT_LE(min_cost, my_cost);
                                 max_cost = std::max(max_cost, my_cost);
                             }
                         };
            each_perm(data, check);
            if (loop_cnt < 1024 || i % 1024 == 0) {
                fprintf(stderr, "  ANDNOT cost(%zu,%s): min: %g, max: %g, factor: %g\n",
                        i, strict ? "strict" : "non-strict", min_cost, max_cost, max_cost / min_cost);
            }
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
