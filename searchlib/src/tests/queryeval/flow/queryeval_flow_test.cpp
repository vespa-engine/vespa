// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/flow.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>
#include <random>

constexpr size_t loop_cnt = 64;

using namespace search::queryeval;

template <typename FLOW>
double ordered_cost_of(const std::vector<FlowStats> &data, InFlow in_flow, bool allow_force_strict) {
    return flow::ordered_cost_of(flow::DirectAdapter(), data, FLOW(in_flow), allow_force_strict);
}

template <typename FLOW>
double dual_ordered_cost_of(const std::vector<FlowStats> &data, InFlow in_flow, bool allow_force_strict) {
    double result = flow::ordered_cost_of(flow::DirectAdapter(), data, FLOW(in_flow), allow_force_strict);
    AnyFlow any_flow = AnyFlow::create<FLOW>(in_flow);
    double total_cost = 0.0;
    for (const auto &item: data) {
        double child_cost = flow::min_child_cost(InFlow(any_flow.strict(), any_flow.flow()), item, allow_force_strict);
        any_flow.update_cost(total_cost, child_cost);
        any_flow.add(item.estimate);
    }
    EXPECT_EQ(total_cost, result);
    return result;
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
    if (size == 0) {
        gen.seed(gen.default_seed);
    }
    return result;
}
void re_seed() { gen_data(0); }

size_t count_perms(size_t n) {
    return (n <= 1) ? 1 : count_perms(n-1) * n;
};

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

std::vector<FlowStats> make_flow_stats(const std::vector<double> &est_list, size_t n) {
    std::vector<FlowStats> result;
    for (size_t i = 0; i < n; ++i) {
        result.emplace_back(est_list[i], 123.0, 456.0);
    }
    return result;
}

void verify_flow(auto flow, const std::vector<double> &est_list, const std::vector<ExpectFlow> &expect) {
    AnyFlow any_flow = AnyFlow::create<decltype(flow)>(InFlow(flow.strict(), flow.flow()));
    ASSERT_EQ(est_list.size() + 1, expect.size());
    for (size_t i = 0; i < est_list.size(); ++i) {
        EXPECT_EQ(any_flow.flow(), flow.flow());
        EXPECT_EQ(any_flow.strict(), flow.strict());
        EXPECT_DOUBLE_EQ(flow.flow(), expect[i].flow);
        EXPECT_EQ(flow.strict(), expect[i].strict);
        EXPECT_DOUBLE_EQ(flow.estimate_of(make_flow_stats(est_list, i)), expect[i].est);
        any_flow.add(est_list[i]);
        flow.add(est_list[i]);
    }
    EXPECT_EQ(any_flow.flow(), flow.flow());
    EXPECT_EQ(any_flow.strict(), flow.strict());
    EXPECT_DOUBLE_EQ(flow.flow(), expect.back().flow);
    EXPECT_EQ(flow.strict(), expect.back().strict);
    EXPECT_DOUBLE_EQ(flow.estimate_of(make_flow_stats(est_list, est_list.size())), expect.back().est);
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
                     {in*0.4, 0.4, false},
                     {in*0.4*0.7, 0.4*0.7, false},
                     {in*0.4*0.7*0.2, 0.4*0.7*0.2, false}});
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
                     {in*0.6, 1.0-0.6, false},
                     {in*0.6*0.3, 1.0-0.6*0.3, false},
                     {in*0.6*0.3*0.8, 1.0-0.6*0.3*0.8, false}});
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
                     {in*0.4, 0.4, false},
                     {in*0.4*0.3, 0.4*0.3, false},
                     {in*0.4*0.3*0.8, 0.4*0.3*0.8, false}});
    }
}

TEST(FlowTest, full_rank_flow) {
    for (bool strict: {false, true}) {
        verify_flow(RankFlow(strict), {0.4, 0.7, 0.2},
                    {{1.0, 0.0, strict},
                     {0.0, 0.4, false},
                     {0.0, 0.4, false},
                     {0.0, 0.4, false}});
    }
}

TEST(FlowTest, partial_rank_flow) {
    for (double in: {1.0, 0.5, 0.25}) {
        verify_flow(RankFlow(in), {0.4, 0.7, 0.2},
                    {{in, 0.0, false},
                     {0.0, 0.4, false},
                     {0.0, 0.4, false},
                     {0.0, 0.4, false}});
    }
}

TEST(FlowTest, full_blender_flow) {
    for (bool strict: {false, true}) {
        verify_flow(BlenderFlow(strict), {0.4, 0.7, 0.2},
                    {{1.0, 0.0, strict},
                     {1.0, 1.0-0.6, strict},
                     {1.0, 1.0-0.6*0.3, strict},
                     {1.0, 1.0-0.6*0.3*0.8, strict}});
    }
}

TEST(FlowTest, partial_blender_flow) {
    for (double in: {1.0, 0.5, 0.25}) {
        verify_flow(BlenderFlow(in), {0.4, 0.7, 0.2},
                    {{in, 0.0, false},
                     {in, 1.0-0.6, false},
                     {in, 1.0-0.6*0.3, false},
                     {in, 1.0-0.6*0.3*0.8, false}});
    }
}

TEST(FlowTest, in_flow_strict_vs_rate_interaction) {
    EXPECT_EQ(InFlow(true).strict(), true);
    EXPECT_EQ(InFlow(true).rate(), 1.0);
    EXPECT_EQ(InFlow(false).strict(), false);
    EXPECT_EQ(InFlow(false).rate(), 1.0);
    EXPECT_EQ(InFlow(0.5).strict(), false);
    EXPECT_EQ(InFlow(0.5).rate(), 0.5);
    EXPECT_EQ(InFlow(true, 0.5).strict(), true);
    EXPECT_EQ(InFlow(true, 0.5).rate(), 1.0);
    EXPECT_EQ(InFlow(false, 0.5).strict(), false);
    EXPECT_EQ(InFlow(false, 0.5).rate(), 0.5);
    EXPECT_EQ(InFlow(-1.0).strict(), false);
    EXPECT_EQ(InFlow(-1.0).rate(), 0.0);
}

TEST(FlowTest, flow_cost) {
    std::vector<FlowStats> data = {{0.4, 1.1, 0.6}, {0.7, 1.2, 0.5}, {0.2, 1.3, 0.4}};
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<AndFlow>(data, false, false), 1.1 + 0.4*1.2 + 0.4*0.7*1.3);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<AndFlow>(data, true, false), 0.6 + 0.4*1.2 + 0.4*0.7*1.3);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<OrFlow>(data, false, false), 1.1 + 0.6*1.2 + 0.6*0.3*1.3);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<OrFlow>(data, true, false), 0.6 + 0.5 + 0.4);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<AndNotFlow>(data, false, false), 1.1 + 0.4*1.2 + 0.4*0.3*1.3);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<AndNotFlow>(data, true, false), 0.6 + 0.4*1.2 + 0.4*0.3*1.3);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<RankFlow>(data, false, false), 1.1);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<RankFlow>(data, true, false), 0.6);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<BlenderFlow>(data, false, false), 1.3);
    EXPECT_DOUBLE_EQ(dual_ordered_cost_of<BlenderFlow>(data, true, false), 0.6);
}

TEST(FlowTest, rank_flow_cost_accumulation_is_first) {
    for (bool strict: {false, true}) {
        auto flow = AnyFlow::create<RankFlow>(strict);
        double cost = 0.0;
        flow.update_cost(cost, 5.0);
        EXPECT_EQ(cost, 5.0);
        flow.add(0.5); // next child
        flow.update_cost(cost, 5.0);
        EXPECT_EQ(cost, 5.0);
    }
}

TEST(FlowTest, blender_flow_cost_accumulation_is_max) {
    for (bool strict: {false, true}) {
        auto flow = AnyFlow::create<BlenderFlow>(strict);
        double cost = 0.0;
        flow.update_cost(cost, 5.0);
        EXPECT_EQ(cost, 5.0);
        flow.add(0.5); // next child
        flow.update_cost(cost, 3.0);
        EXPECT_EQ(cost, 5.0);
        flow.add(0.5); // next child
        flow.update_cost(cost, 7.0);
        EXPECT_EQ(cost, 7.0);
    }
}

TEST(FlowTest, optimal_and_flow) {
    for (size_t i = 0; i < loop_cnt; ++i) {
        for (bool strict: {false, true}) {
            auto data = gen_data(7);
            double ref_est = AndFlow::estimate_of(data);
            double min_cost = AndFlow::cost_of(data, strict);
            double max_cost = 0.0;
            AndFlow::sort(data, strict);
            EXPECT_EQ(ordered_cost_of<AndFlow>(data, strict, false), min_cost);
            auto check = [&](const std::vector<FlowStats> &my_data) noexcept {
                             double my_cost = ordered_cost_of<AndFlow>(my_data, strict, false);
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
            EXPECT_EQ(ordered_cost_of<OrFlow>(data, strict, false), min_cost);
            auto check = [&](const std::vector<FlowStats> &my_data) noexcept {
                             double my_cost = ordered_cost_of<OrFlow>(my_data, strict, false);
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
            EXPECT_DOUBLE_EQ(ordered_cost_of<AndNotFlow>(data, strict, false), min_cost);
            auto check = [&](const std::vector<FlowStats> &my_data) noexcept {
                             if (my_data[0] == first) {
                                 double my_cost = ordered_cost_of<AndNotFlow>(my_data, strict, false);
                                 EXPECT_LE(min_cost, my_cost + 1e-9);
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

void test_strict_AND_sort_strategy(auto my_sort) {
    re_seed();
    // size_t max_work = 500'000'000;
    size_t max_work = 1;
    for (size_t child_cnt: {2, 3, 5, 7, 9}) {
        size_t cnt = std::max(size_t(10), std::min(size_t(128'000), (max_work / count_perms(child_cnt))));
        double max_rel_err = 0.0;
        double sum_rel_err = 0.0;
        std::vector<double> errs;
        errs.reserve(cnt);
        auto p = [&](double arg){
                     size_t idx = std::lround(arg * (errs.size() - 1));
                     if (idx < errs.size()) {
                         return errs[idx];
                     }
                     return errs.back();
                 };
        for (size_t i = 0; i < cnt; ++i) {
            auto data = gen_data(child_cnt);
            double ref_est = AndFlow::estimate_of(data);
            double min_cost = 1'000'000.0;
            double max_cost = 0.0;
            my_sort(data);
            double est_cost = ordered_cost_of<AndFlow>(data, true, true);
            auto check = [&](const std::vector<FlowStats> &my_data) noexcept {
                             double my_cost = ordered_cost_of<AndFlow>(my_data, true, true);
                             min_cost = std::min(min_cost, my_cost);
                             max_cost = std::max(max_cost, my_cost);
                         };
            each_perm(data, check);
            double rel_err = 0.0;
            double cost_range = (max_cost - min_cost);
            if (cost_range > 1e-9) {
                rel_err = (est_cost - min_cost) / cost_range;
            }
            max_rel_err = std::max(max_rel_err, rel_err);
            sum_rel_err += rel_err;
            errs.push_back(rel_err);
            EXPECT_NEAR(ref_est, AndFlow::estimate_of(data), 1e-9);
        }
        std::sort(errs.begin(), errs.end());
        fprintf(stderr, "  AND/%zu: avg: %10f, p90: %10f, p99: %10f, p99.9: %10f, max: %10f\n",
                child_cnt, (sum_rel_err / cnt), p(0.9), p(0.99), p(0.999), max_rel_err);
    }
}

TEST(FlowTest, strict_and_with_allow_force_strict_basic_order) {
    auto my_sort = [](auto &data){ AndFlow::sort(data, true); };
    test_strict_AND_sort_strategy(my_sort);
}

void greedy_sort(std::vector<FlowStats> &data, auto flow, auto score_of) {
    InFlow in_flow = InFlow(flow.strict(), flow.flow());
    for (size_t next = 0; (next + 1) < data.size(); ++next) {
        size_t best_idx = next;
        double best_score = score_of(in_flow, data[next]);
        for (size_t i = next + 1; i < data.size(); ++i) {
            double score = score_of(in_flow, data[i]);
            if (score > best_score) {
                best_score = score;
                best_idx = i;
            }
        }
        std::swap(data[next], data[best_idx]);
        flow.add(data[next].estimate);
    }
}

TEST(FlowTest, strict_and_with_allow_force_strict_greedy_reduction_efficiency) {
    auto score_of = [](InFlow in_flow, const FlowStats &stats) {
                        double child_cost = flow::min_child_cost(in_flow, stats, true);
                        double reduction = (1.0 - stats.estimate);
                        return reduction / child_cost;
                    };
    auto my_sort = [&](auto &data){ greedy_sort(data, AndFlow(true), score_of); };
    test_strict_AND_sort_strategy(my_sort);
}

TEST(FlowTest, strict_and_with_allow_force_strict_partitioning) {
    auto my_sort = [](auto &data) {
                       AndFlow::sort(data, false);
                       double rate = 1.0;
                       size_t a = 0;
                       for (size_t b = 0; b < data.size(); ++b) {
                           double est = data[b].estimate;
                           if (flow::should_force_strict(data[b], rate)) {
                               auto pos = data.begin() + b;
                               std::rotate(data.begin() + a, pos, pos + 1);
                               ++a;
                           }
                           rate *= est;
                       }
                       if (a == 0) { // need at least 1 strict child
                           AndFlow::sort(data, true);
                       }
                   };
    test_strict_AND_sort_strategy(my_sort);
}

TEST(FlowTest, strict_and_with_allow_force_strict_incremental_strict_selection) {
    auto my_sort = [](auto &data) {
                       AndFlow::sort(data, true);
                       for (size_t next = 1; (next + 1) < data.size(); ++next) {
                           auto [idx, score] = flow::select_forced_strict_and_child(flow::DirectAdapter(), data, next);
                           if (score >= 0.0) {
                               break;
                           }
                           auto pos = data.begin() + idx;
                           std::rotate(data.begin() + next, pos, pos + 1);
                       }
                   };
    test_strict_AND_sort_strategy(my_sort);
}

TEST(FlowTest, strict_and_with_allow_force_strict_incremental_strict_selection_with_strict_re_sorting) {
    auto my_sort = [](auto &data) {
                       AndFlow::sort(data, true);
                       size_t strict_cnt = 1;
                       for (; strict_cnt < data.size(); ++strict_cnt) {
                           auto [idx, score] = flow::select_forced_strict_and_child(flow::DirectAdapter(), data, strict_cnt);
                           if (score >= 0.0) {
                               break;
                           }
                           auto pos = data.begin() + idx;
                           std::rotate(data.begin() + strict_cnt, pos, pos + 1);
                       }
                       std::sort(data.begin(), data.begin() + strict_cnt,
                                 [](const auto &a, const auto &b){ return (a.estimate < b.estimate); });
                   };
    test_strict_AND_sort_strategy(my_sort);
}

TEST(FlowTest, strict_and_with_allow_force_strict_incremental_strict_selection_with_order) {
    auto my_sort = [](auto &data) {
                       AndFlow::sort(data, true);
                       for (size_t next = 1; next < data.size(); ++next) {
                           auto [idx, target, score] = flow::select_forced_strict_and_child_with_order(flow::DirectAdapter(), data, next);
                           if (score >= 0.0) {
                               break;
                           }
                           auto pos = data.begin() + idx;
                           std::rotate(data.begin() + target, pos, pos + 1);
                       }
                   };
    test_strict_AND_sort_strategy(my_sort);
}

TEST(FlowTest, strict_and_with_allow_force_strict_assume_strict_then_partition_and_re_sort_non_strict) {
    auto my_sort = [](auto &data) {
                       std::sort(data.begin(), data.end(),
                                 [](const auto &a, const auto &b){ return (a.estimate < b.estimate); });
                       double est = 1.0;
                       size_t last = data.size();
                       auto best_strict = [&](size_t i)noexcept{
                                              if (i == 0) {
                                                  return data[i].strict_cost < data[i].cost;
                                              } else {
                                                  return flow::should_force_strict(data[i], est);
                                              }
                                          };
                       for (size_t i = 0; i < last; ++i) {
                           while (i < last && !best_strict(i)) {
                               std::rotate(data.begin()+i, data.begin()+i+1, data.begin()+last);
                               --last;
                           }
                           est *= data[i].estimate;
                       }
                       if (last > 0) {
                           std::sort(data.begin() + last, data.end(), flow::MinAndCost(flow::DirectAdapter()));
                       } else {
                           AndFlow::sort(data, true);
                       }
                   };
    test_strict_AND_sort_strategy(my_sort);
}

GTEST_MAIN_RUN_ALL_TESTS()
